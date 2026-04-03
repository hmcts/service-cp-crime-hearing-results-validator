package uk.gov.hmcts.cp.simulation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds JSON payloads for the POST /api/validation/validate endpoint.
 * Each static method returns a complete DraftValidationRequest JSON string
 * matching one of the DR-SENT-002 acceptance criteria scenarios.
 */
public final class PayloadBuilder {

    private static final String[] CUSTODIAL_CODES = {
        "IMP", "DTO", "YOI", "extdvs", "extdvsu", "extivs", "STSDY", "specc", "speccc", "speccd"
    };
    private static final String[] COURT_TYPES = {"MAGISTRATES", "CROWN", "YOUTH"};
    private static final String[] FIRST_NAMES = {
        "James", "Sarah", "Mohammed", "Emily", "David", "Sophie", "Michael", "Jessica", "Robert", "Hannah"
    };
    private static final String[] LAST_NAMES = {
        "Smith", "Jones", "Williams", "Brown", "Taylor", "Davies", "Wilson", "Evans", "Thomas", "Roberts"
    };
    private static final String[] OFFENCE_CODES = {
        "TH68001", "AS001", "CD001", "DA001", "FR001", "OF001", "PO001", "RB001", "VL001", "WP001"
    };
    private static final String[] OFFENCE_TITLES = {
        "Theft", "Assault", "Criminal Damage", "Dangerous Driving",
        "Fraud", "Offensive Weapon", "Possession", "Robbery", "Violence", "Weapons Possession"
    };

    private PayloadBuilder() {
    }

    /**
     * AC1: Single offence with no concurrent/consecutive info — valid, no error.
     * One defendant, two offences, two custodial results.
     * Only one offence has no concurrent/consecutive info (the primary).
     */
    public static String ac1NoError() {
        String defId = uid();
        String off1 = uid();
        String off2 = uid();
        String code = randomCustodialCode();

        return request(
            defendants(defendant(defId, randomFirst(), randomLast())),
            offences(
                offence(off1, "TH68001", "Theft", 1, randomUrn()),
                offence(off2, "AS001", "Assault", 2, randomUrn())
            ),
            resultLines(
                resultLine(uid(), code, "Imprisonment", defId, off1, null, null),
                resultLine(uid(), code, "Imprisonment", defId, off2, true, null)
            )
        );
    }

    /**
     * AC2: Multiple offences missing concurrent/consecutive info — ERROR.
     * One defendant, three offences, three custodial results.
     * Two offences have no concurrent/consecutive info.
     */
    public static String ac2Error() {
        String defId = uid();
        String off1 = uid();
        String off2 = uid();
        String off3 = uid();
        String code = randomCustodialCode();

        return request(
            defendants(defendant(defId, randomFirst(), randomLast())),
            offences(
                offence(off1, "TH68001", "Theft", 1, randomUrn()),
                offence(off2, "AS001", "Assault", 2, randomUrn()),
                offence(off3, "CD001", "Criminal Damage", 3, randomUrn())
            ),
            resultLines(
                resultLine(uid(), code, "Imprisonment", defId, off1, null, null),
                resultLine(uid(), code, "Imprisonment", defId, off2, null, null),
                resultLine(uid(), code, "Imprisonment", defId, off3, true, null)
            )
        );
    }

    /**
     * AC3: Offence with both concurrent AND consecutive info — WARNING.
     * One defendant, two offences, two custodial results.
     * One offence has both isConcurrent=true and consecutiveToOffence set.
     */
    public static String ac3Warning() {
        String defId = uid();
        String off1 = uid();
        String off2 = uid();
        String code = randomCustodialCode();

        return request(
            defendants(defendant(defId, randomFirst(), randomLast())),
            offences(
                offence(off1, "TH68001", "Theft", 1, randomUrn()),
                offence(off2, "AS001", "Assault", 2, randomUrn())
            ),
            resultLines(
                resultLine(uid(), code, "Imprisonment", defId, off1, null, null),
                resultLine(uid(), code, "Imprisonment", defId, off2, true, off1)
            )
        );
    }

    /**
     * AC4: All offences have concurrent/consecutive info, no primary — WARNING.
     * One defendant, two offences, two custodial results.
     * Both offences have concurrent info set.
     */
    public static String ac4Warning() {
        String defId = uid();
        String off1 = uid();
        String off2 = uid();
        String code = randomCustodialCode();

        return request(
            defendants(defendant(defId, randomFirst(), randomLast())),
            offences(
                offence(off1, "TH68001", "Theft", 1, randomUrn()),
                offence(off2, "AS001", "Assault", 2, randomUrn())
            ),
            resultLines(
                resultLine(uid(), code, "Imprisonment", defId, off1, true, null),
                resultLine(uid(), code, "Imprisonment", defId, off2, true, null)
            )
        );
    }

    /**
     * Picks a random scenario weighted to approximate production distribution:
     * 40% AC1 (no error), 25% AC2 (error), 20% AC3 (warning), 15% AC4 (warning).
     */
    public static String randomWeighted() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 40) {
            return ac1NoError();
        } else if (roll < 65) {
            return ac2Error();
        } else if (roll < 85) {
            return ac3Warning();
        } else {
            return ac4Warning();
        }
    }

    // ---- JSON building helpers ----

    private static String request(String defendants, String offences, String resultLines) {
        return """
            {
              "hearingId": "%s",
              "caseId": "%s",
              "hearingDay": "%s",
              "courtType": "%s",
              "defendants": [%s],
              "offences": [%s],
              "resultLines": [%s]
            }""".formatted(
            uid(), uid(),
            LocalDate.now().minusDays(ThreadLocalRandom.current().nextInt(30)).toString(),
            COURT_TYPES[ThreadLocalRandom.current().nextInt(COURT_TYPES.length)],
            defendants, offences, resultLines
        );
    }

    private static String defendant(String id, String firstName, String lastName) {
        return """
            {"id": "%s", "firstName": "%s", "lastName": "%s"}""".formatted(id, firstName, lastName);
    }

    private static String offence(String id, String code, String title, int orderIndex, String caseUrn) {
        return """
            {"id": "%s", "offenceCode": "%s", "offenceTitle": "%s", "orderIndex": %d, "caseUrn": "%s"}"""
            .formatted(id, code, title, orderIndex, caseUrn);
    }

    private static String resultLine(String id, String shortCode, String label,
                                     String defendantId, String offenceId,
                                     Boolean isConcurrent, String consecutiveToOffence) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\": \"").append(id).append("\"");
        sb.append(", \"shortCode\": \"").append(shortCode).append("\"");
        sb.append(", \"label\": \"").append(label).append("\"");
        sb.append(", \"defendantId\": \"").append(defendantId).append("\"");
        sb.append(", \"offenceId\": \"").append(offenceId).append("\"");
        if (isConcurrent != null) {
            sb.append(", \"isConcurrent\": ").append(isConcurrent);
        }
        if (consecutiveToOffence != null) {
            sb.append(", \"consecutiveToOffence\": \"").append(consecutiveToOffence).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String defendants(String... items) {
        return String.join(", ", items);
    }

    private static String offences(String... items) {
        return String.join(", ", items);
    }

    private static String resultLines(String... items) {
        return String.join(", ", items);
    }

    private static String uid() {
        return UUID.randomUUID().toString();
    }

    private static String randomCustodialCode() {
        return CUSTODIAL_CODES[ThreadLocalRandom.current().nextInt(CUSTODIAL_CODES.length)];
    }

    private static String randomFirst() {
        return FIRST_NAMES[ThreadLocalRandom.current().nextInt(FIRST_NAMES.length)];
    }

    private static String randomLast() {
        return LAST_NAMES[ThreadLocalRandom.current().nextInt(LAST_NAMES.length)];
    }

    private static String randomUrn() {
        return ThreadLocalRandom.current().nextInt(10, 99) + "SB"
            + ThreadLocalRandom.current().nextInt(100000, 999999);
    }
}
