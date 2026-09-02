package com.infrai.example.release;

/**
 * Pins the locking rule down, since that is the decision the creator's signature depends on.
 * Run it with ./test.sh.
 */
public final class ReleaseDecisionTest {

    private static int failures;

    public static void main(String[] args) {
        ReleaseFormService service = new ReleaseFormService(null, DeliveryConfig.load("config/delivery.properties"));

        MediaAsset complete = new MediaAsset("ast_1", "Pilot", "Mara Okonjo",
                "mara@example.com", "Worldwide", 55, "job_1");

        ReleaseForm locked = service.fill(complete, "completed");
        check("finished job with every field set locks the release", locked.locked);
        check("locked release has nothing outstanding", locked.missingFields.isEmpty());
        check("locked release carries the mode into the template",
                "locked".equals(locked.templateVars.get("form_mode")));
        check("idempotency key is derived from asset and mode",
                "ast_1:locked".equals(locked.idempotencyKey));

        ReleaseForm stillRunning = service.fill(complete, "transcoding");
        check("an unfinished job keeps the form editable", !stillRunning.locked);

        MediaAsset noTerritory = new MediaAsset("ast_2", "Pilot", "Mara Okonjo",
                "mara@example.com", "  ", 55, "job_2");
        ReleaseForm partial = service.fill(noTerritory, "completed");
        check("a blank territory keeps the form editable", !partial.locked);
        check("the blank field is named back to the caller",
                partial.missingFields.contains("territory"));

        MediaAsset badShare = new MediaAsset("ast_3", "Pilot", "Mara Okonjo",
                "mara@example.com", "Worldwide", 0, "job_3");
        check("an out-of-range revenue share is reported",
                service.fill(badShare, "completed").missingFields.contains("revenue_share_percent"));

        check("the editable template renders writable boxes",
                ReleaseFormService.template(false).contains("<input"));
        check("the locked template renders flat text only",
                !ReleaseFormService.template(true).contains("<input"));

        if (failures > 0) {
            System.out.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("all checks passed");
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("ok   " + label);
        } else {
            failures++;
            System.out.println("FAIL " + label);
        }
    }
}
