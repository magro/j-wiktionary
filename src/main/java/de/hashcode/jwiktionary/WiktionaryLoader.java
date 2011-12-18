/**
 * 
 */
package de.hashcode.jwiktionary;

import static fj.data.Option.none;
import static fj.data.Option.some;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mediawiki.importer.DumpWriter;
import org.mediawiki.importer.LatestFilter;
import org.mediawiki.importer.Page;
import org.mediawiki.importer.Revision;
import org.mediawiki.importer.Siteinfo;
import org.mediawiki.importer.XmlDumpReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Closeables;

import de.tudarmstadt.ukp.wikipedia.parser.Content;
import de.tudarmstadt.ukp.wikipedia.parser.ParsedPage;
import de.tudarmstadt.ukp.wikipedia.parser.Section;
import de.tudarmstadt.ukp.wikipedia.parser.Template;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;
import fj.F;
import fj.data.Option;

/**
 * Loads a provided wiktionary dump in xml format (e.g. an unpacked <a
 * href="http://dumps.wikimedia.org/dewiktionary/latest/dewiktionary-latest-pages-articles.xml.bz2">
 * dewiktionary-latest-pages-articles.xml.bz2</a>) and remembers all pages (a page represents a word) that are marked to
 * be nouns. Those can be accessed via {@link #getNounTitles()} or {@link #isNoun(String)}.
 * 
 * @author Martin Grotzke
 */
public class WiktionaryLoader {

    private static final class PageTitleNounCollector implements DumpWriter {

        private final F<Template, String> getFirstParameter = getParameter(0);
        private final F<String, Boolean> isNoun = new F<String, Boolean>() {

            @Override
            public Boolean f(final String a) {
                return "Substantiv".equals(a);
            }

        };
        private final MediaWikiParser parser;
        private final Set<String> nounTitles;

        private String pageTitle;

        private PageTitleNounCollector(final MediaWikiParser parser) {
            this.parser = parser;
            nounTitles = new HashSet<String>();
        }

        public Set<String> getNounTitles() {
            return nounTitles;
        }

        @Override
        public void writeStartWiki() throws IOException {
        }

        @Override
        public void writeStartPage(final Page page) throws IOException {
            this.pageTitle = page.Title.Text;
        }

        @Override
        public void writeSiteinfo(final Siteinfo arg0) throws IOException {
        }

        @Override
        public void writeRevision(final Revision rev) throws IOException {
            final ParsedPage pp = parser.parse(rev.Text);
            if (pp == null) {
                LOGGER.warn("Could not parse page with title {}", pageTitle);
            } else if (pp.getSections() != null && !pp.getSections().isEmpty()) {
                final Section section = pp.getSection(0);
                final List<Content> contentList = section.getContentList();
                if (contentList.size() > 1 && contentList.get(1) instanceof Section) {
                    final Section subSection = (Section) contentList.get(1);
                    final Option<Boolean> isNounOption = getTemplate(subSection.getTemplates(), "Wortart").map(
                            getFirstParameter).map(isNoun);
                    if (isNounOption.isSome() && isNounOption.some().booleanValue()) {
                        nounTitles.add(pageTitle);
                    }
                }
            }
        }

        private Option<Template> getTemplate(final List<Template> templates, final String name) {
            for (final Template template : templates) {
                if (name.equals(template.getName())) {
                    return some(template);
                }
            }
            return none();
        }

        @Override
        public void writeEndWiki() throws IOException {
        }

        @Override
        public void writeEndPage() throws IOException {
            pageTitle = null;
        }

        @Override
        public void close() throws IOException {
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(WiktionaryLoader.class);

    private static F<Template, String> getParameter(final int paramIndex) {
        return new F<Template, String>() {

            @Override
            public String f(final Template a) {
                return a.getParameters().get(paramIndex);
            }
        };

    }

    private Set<String> nounTitles;

    /**
     * Create a new instance with a handle to the (unpacked) xml pages/articles dump.
     * 
     * @param wiktionaryDump
     *            the unpacked xml dump.
     * 
     * @throws FileNotFoundException
     *             if the provided wiktionaryDump does not exist.
     */
    public WiktionaryLoader(final File wiktionaryDump) throws FileNotFoundException {
        // get a ParsedPage object
        final MediaWikiParserFactory pf = new MediaWikiParserFactory();
        final MediaWikiParser parser = pf.createParser();

        final FileInputStream fis = new FileInputStream(wiktionaryDump);
        final PageTitleNounCollector pageTitleNounCollector = new PageTitleNounCollector(parser);
        final XmlDumpReader dumpReader = new XmlDumpReader(fis, new LatestFilter(pageTitleNounCollector));
        try {
            dumpReader.readDump();
            nounTitles = pageTitleNounCollector.getNounTitles();
            LOGGER.info("Loaded {} nouns.", nounTitles.size());
        } catch (final IOException e) {
            LOGGER.error("An error occurred when trying to read dump.", e);
            throw new RuntimeException(e);
        } finally {
            Closeables.closeQuietly(fis);
        }
    }

    public boolean isNoun(final String word) {
        return nounTitles.contains(word);
    }

    public Set<String> getNounTitles() {
        return nounTitles;
    }

    public static void main(final String[] args) throws IOException {
        // final File file = new File(TitleCacheLoader.class.getResource("/sample-pages-articles.xml").getFile());
        final File file = new File("/home/magro/proj/j-wiktionary/dewiktionary-20111210-pages-articles.xml");
        final WiktionaryLoader loader = new WiktionaryLoader(file);
        LOGGER.info("Have nounTitles: {}", loader.getNounTitles().size());
    }

}
