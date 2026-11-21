package luatweaker.pmd.probe;

/**
 * Canary for the PMD gate. It deliberately violates {@code SystemPrintln}: the
 * canary analysis asserts that exactly this finding comes back, which proves
 * {@code config/pmd/ruleset.xml} actually loaded and ran. Keep this file free of
 * every other violation so the assertion stays unambiguous.
 */
final class Probe {

    void canary() {
        System.out.println("pmd canary");
    }
}
