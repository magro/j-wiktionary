/**
 * 
 */
package de.hashcode.jwiktionary;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Set;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.Sets;

/**
 * Unit test for {@link WiktionaryLoader}.
 * 
 * @author Martin Grotzke
 */
public class WiktionaryLoaderTest {

    @DataProvider
    public static Object[][] samplePages() {
        // @formatter:off
        return new Object[][] {
                { "Januar.xml", Sets.newHashSet("Januar", "Januare", "Januaren", "Januars") },
                { "Kleid.xml", Sets.newHashSet("Kleid", "Kleidern", "Kleides", "Kleider") },
                { "Spielwiese.xml", Sets.newHashSet("Spielwiese", "Spielwiesen") },
                { "sein.xml", null },
                { "Nomen_proprium.xml", Sets.newHashSet("Nomen proprium", "Nomina propria") }
            };
        // @formatter:on
    }

    @Test(dataProvider = "samplePages")
    public void testSamplePages(final String xmlFileName, final Set<String> expectedNoun) throws FileNotFoundException {
        final WiktionaryLoader cut = new WiktionaryLoader(new File(getClass().getResource("/" + xmlFileName).getFile()));
        if (expectedNoun == null) {
            assertTrue(cut.getNounTitles().isEmpty(), "NounTitles not empty: " + cut.getNounTitles());
        } else {
            assertEquals(cut.getNounTitles(), expectedNoun);
        }
    }
}
