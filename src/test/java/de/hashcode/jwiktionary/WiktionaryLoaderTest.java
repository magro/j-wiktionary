/**
 * 
 */
package de.hashcode.jwiktionary;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
                { "Januar.xml", 1, "Januar" },
                { "Kleid.xml", 1, "Kleid" },
                { "Spielwiese.xml", 1, "Spielwiese" },
                { "sein.xml", 0, null },
                { "Nomen_proprium.xml", 1, "Nomen proprium" }
            };
        // @formatter:on
    }

    @Test(dataProvider = "samplePages")
    public void testSamplePages(final String xmlFileName, final int expectedWordCount, final String expectedNoun)
            throws FileNotFoundException {
        final WiktionaryLoader cut = new WiktionaryLoader(new File(getClass().getResource("/" + xmlFileName).getFile()));
        assertEquals(cut.getNounTitles().size(), expectedWordCount);
        if (expectedNoun != null)
            assertTrue(cut.isNoun(expectedNoun));
    }
}
