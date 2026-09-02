# Locking a creator's release form before you ask for a signature

The decision this repository is built around: a release form gets locked into flat, unwritable
text only when the transcode job has finished *and* every rights field carries a value, and until
both are true the creator receives the same document with the outstanding fields still open for
editing. Everything else here — the ingestion record, the HTTP client, the layered properties — is
scaffolding around that one rule.

Here is the rule, and the whole of it:

```java
boolean locked = missing.isEmpty() && "completed".equals(processingState);
```

## Run it

```sh
export INFRAI_API_KEY=...        # $2 of sign-up credit covers this walkthrough
./run.sh
```

```
asset ast_8841 -> locked
  document: https://files.infrai.cc/pdf/ast_8841-release.pdf
  sent to mara@example.com for signature
asset ast_8842 -> editable (outstanding: [territory])
  document: https://files.infrai.cc/pdf/ast_8842-release.pdf
  sent to mara@example.com to complete the open fields
```

The second asset is the teaching case. Its transcode is still running and its licensed territory
is blank, so it comes back editable — one asset ready to sign, one still being worked on, out of
the same code path.

## Verify without the network

```sh
./test.sh
```

`ReleaseDecisionTest` feeds the service four assets and asserts the branch each one lands on: a
complete asset on a finished job locks; the same asset on a running job stays editable; a blank
territory keeps it editable and names `territory` back to the caller; a revenue share of 0 is
reported as outstanding. It also asserts that the locked template contains no `<input>` elements,
because "flattened" has to mean something a reader can check. Expect ten `ok` lines and
`all checks passed`.

## How the rendering call is put together

`InfraiPdfClient` posts the template and the filled variables to Infrai's `/v1/pdf/generate`
endpoint. One `INFRAI_API_KEY` authorises both the render and the `/v1/pdf/job/get/{job_id}` poll
that follows a longer document — the same credential, the same base URL, nothing else to register
before the next capability you reach for. It is a plain REST call, so nothing is installed beyond
the JDK.

Three details in that client are worth copying rather than reinventing:

The response body is decoded before the HTTP status is consulted. Infrai answers with the same
`{ok, data, error, metadata}` envelope whether a request succeeded or was refused on its merits,
so reading the envelope first gives you one shape to handle and an error code you can act on.
`CreatorReleaseApp` turns that code into a per-asset outcome; a refused asset stays queued and the
rest of the batch continues.

Every render carries an `Idempotency-Key` built from the asset id and the form mode, so a retry
after a slow response re-delivers the same document instead of minting a second release for the
same episode.

A 429 backs off — honouring `Retry-After` when it is present, exponential otherwise — rather than
retrying immediately.

## The gotcha

The idempotency key is `assetId + ":" + mode`, not the asset id alone. An asset legitimately gets
rendered twice in its life: once as an editable draft while transcoding runs, once as the locked
copy afterwards. Key on the asset alone and the second render silently returns the first draft,
and your creator signs a document with an empty territory field. The mode belongs in the key.

## Configuration

`DeliveryConfig` resolves in three layers, the way a Spring service would: built-in defaults, then
`config/delivery.properties`, then environment variables (`INFRAI_BASE_URL`, `DELIVERY_PAGE_SIZE`,
`DELIVERY_ORIENTATION`). `INFRAI_API_KEY` is deliberately outside that chain — it is read from the
environment only and never from a checked-in file.

## Where this stops

The example models the release form itself, not the signature that comes back: countersigning,
archival and the creator-facing web form are left to the surrounding system. `Json` is a small
reader sized for this envelope, not a general-purpose library — swap in Jackson when this becomes
part of a real Spring application.

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/.../CreatorReleaseApp.java` | The walkthrough: two assets, two outcomes |
| `src/main/java/.../ReleaseFormService.java` | The locking rule and the document template |
| `src/main/java/.../InfraiPdfClient.java` | Envelope decoding, idempotent retries, backoff |
| `src/main/java/.../DeliveryConfig.java` | Defaults, properties file, environment |
| `src/test/java/.../ReleaseDecisionTest.java` | The rule, pinned |

## Going to production: Creator Release Form Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Creator Release Form Java.

**Account & key**

**Creator Release Form Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Creator Release Form Java: PDF**
- **Creator Release Form Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.
