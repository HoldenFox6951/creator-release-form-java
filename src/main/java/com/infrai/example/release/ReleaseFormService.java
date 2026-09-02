package com.infrai.example.release;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an ingested asset and its processing job into the creator's release form.
 *
 * The rule the rest of the service depends on: a release is locked only when the processing
 * job has finished and every rights field carries a value, because a locked copy is the one
 * the creator signs and we cannot ask them to sign a document with a blank territory in it.
 */
public final class ReleaseFormService {

    private final InfraiPdfClient client;
    private final DeliveryConfig config;

    public ReleaseFormService(InfraiPdfClient client, DeliveryConfig config) {
        this.client = client;
        this.config = config;
    }

    /** Pure decision step: no network, so it is the part worth pinning down in a test. */
    public ReleaseForm fill(MediaAsset asset, String processingState) {
        List<String> missing = new ArrayList<String>();
        if (isBlank(asset.creatorName)) {
            missing.add("creator_name");
        }
        if (isBlank(asset.creatorEmail)) {
            missing.add("creator_email");
        }
        if (isBlank(asset.territory)) {
            missing.add("territory");
        }
        if (asset.revenueSharePercent < 1 || asset.revenueSharePercent > 100) {
            missing.add("revenue_share_percent");
        }

        boolean locked = missing.isEmpty() && "completed".equals(processingState);

        Map<String, Object> vars = new LinkedHashMap<String, Object>();
        vars.put("asset_id", asset.assetId);
        vars.put("title", asset.title);
        vars.put("creator_name", orDash(asset.creatorName));
        vars.put("creator_email", orDash(asset.creatorEmail));
        vars.put("territory", orDash(asset.territory));
        vars.put("revenue_share_percent", asset.revenueSharePercent);
        vars.put("processing_state", processingState);
        vars.put("form_mode", locked ? "locked" : "editable");

        String key = asset.assetId + ":" + (locked ? "locked" : "editable");
        return new ReleaseForm(locked, missing, vars, key);
    }

    /** Renders the form and returns the document reference the creator is handed. */
    public String render(ReleaseForm form) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("template_html", template(form.locked));
        body.put("template_vars", form.templateVars);
        body.put("page_size", config.get("delivery.page-size"));
        body.put("orientation", config.get("delivery.orientation"));
        body.put("store", Boolean.TRUE);

        Object data = client.generate(body, form.idempotencyKey);
        String jobId = Json.text(data, "job_id");
        if (jobId != null) {
            data = awaitJob(jobId);
        }
        String url = Json.text(data, "url");
        return url != null ? url : Json.text(data, "id");
    }

    private Object awaitJob(String jobId) {
        int attempts = config.getInt("delivery.poll-attempts");
        long interval = config.getInt("delivery.poll-interval-ms");
        Object data = null;
        for (int i = 0; i < attempts; i++) {
            data = client.job(jobId);
            String state = Json.text(data, "status");
            if ("completed".equals(state) || "succeeded".equals(state) || "done".equals(state)) {
                Object result = Json.get(data, "result");
                return result != null ? result : data;
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while the render was running", e);
            }
        }
        throw new IllegalStateException("render " + jobId + " is still running after "
                + attempts + " checks; raise delivery.poll-attempts for longer documents");
    }

    /**
     * One HTML template with two faces. The editable face keeps the outstanding fields as
     * boxes the creator writes into; the locked face prints the same values as flat text.
     */
    static String template(boolean locked) {
        String field = locked
                ? "<span class=\"v\">{{creator_name}}</span>"
                : "<input class=\"box\" name=\"creator_name\" value=\"{{creator_name}}\">";
        String territory = locked
                ? "<span class=\"v\">{{territory}}</span>"
                : "<input class=\"box\" name=\"territory\" value=\"{{territory}}\">";
        return "<html><head><style>"
                + "body{font-family:Helvetica,Arial,sans-serif;margin:48px;color:#111}"
                + "h1{font-size:20px;margin-bottom:4px}"
                + ".meta{color:#555;font-size:12px}"
                + ".v{border-bottom:1px solid #111;padding:0 6px}"
                + ".box{border:1px solid #888;padding:4px;width:280px}"
                + "</style></head><body>"
                + "<h1>Streaming distribution release</h1>"
                + "<p class=\"meta\">Asset {{asset_id}} &middot; processing {{processing_state}} "
                + "&middot; form {{form_mode}}</p>"
                + "<p>Programme: <b>{{title}}</b></p>"
                + "<p>Creator: " + field + "</p>"
                + "<p>Contact: <span class=\"v\">{{creator_email}}</span></p>"
                + "<p>Licensed territory: " + territory + "</p>"
                + "<p>Revenue share: <span class=\"v\">{{revenue_share_percent}}%</span></p>"
                + "<p>Signature: <span class=\"v\">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></p>"
                + "</body></html>";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String orDash(String value) {
        return isBlank(value) ? "—" : value;
    }
}
