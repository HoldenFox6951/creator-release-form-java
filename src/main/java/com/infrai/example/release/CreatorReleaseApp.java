package com.infrai.example.release;

/**
 * Runnable walkthrough of the whole path: two ingested assets arrive, the service decides
 * which of them is ready to be locked, and only the ready one is rendered and delivered.
 */
public final class CreatorReleaseApp {

    public static void main(String[] args) {
        DeliveryConfig config = DeliveryConfig.load("config/delivery.properties");
        InfraiPdfClient client = new InfraiPdfClient(config.get("infrai.base-url"), config.apiKey());
        ReleaseFormService service = new ReleaseFormService(client, config);

        MediaAsset ready = new MediaAsset(
                "ast_8841",
                "Night Shift, Episode 4",
                "Mara Okonjo",
                "mara@example.com",
                "Worldwide excluding Japan",
                55,
                "job_transcode_8841");

        MediaAsset waiting = new MediaAsset(
                "ast_8842",
                "Night Shift, Episode 5",
                "Mara Okonjo",
                "mara@example.com",
                "",
                55,
                "job_transcode_8842");

        deliver(service, ready, "completed");
        deliver(service, waiting, "transcoding");
    }

    private static void deliver(ReleaseFormService service, MediaAsset asset, String processingState) {
        ReleaseForm form = service.fill(asset, processingState);
        System.out.println("asset " + asset.assetId + " -> " + form.mode()
                + (form.missingFields.isEmpty() ? "" : " (outstanding: " + form.missingFields + ")"));

        try {
            String document = service.render(form);
            System.out.println("  document: " + document);
            if (form.locked) {
                System.out.println("  sent to " + asset.creatorEmail + " for signature");
            } else {
                System.out.println("  sent to " + asset.creatorEmail + " to complete the open fields");
            }
        } catch (InfraiException e) {
            // A refused request is an answer about this asset, so it becomes a 4xx for our
            // own caller and the asset stays in the queue instead of failing the batch.
            System.out.println("  release rejected (" + e.code + "), asset stays queued");
        }
    }
}
