/**
 * 
 */
package de.hashcode.jwiktionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.mediawiki.importer.DumpWriter;
import org.mediawiki.importer.LatestFilter;
import org.mediawiki.importer.NamespaceFilter;
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
        private Page page;
        private static final Pattern SINGULAR_PLURAL_PARAM_PATTERN = Pattern
                .compile(" (Singular|Plural)=(der|die|das|des|dem|den) ");

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
            this.page = page;
        }

        @Override
        public void writeSiteinfo(final Siteinfo arg0) throws IOException {
        }

        @Override
        public void writeRevision(final Revision rev) throws IOException {
            final ParsedPage pp = parser.parse(rev.Text);
            if (pp == null) {
                LOGGER.warn("Could not parse page with title {}", pageTitle);
            } else if (pp.getSections() != null) {

                final Set<String> declinations = getDeclinations(pp.getTemplates());
                if (!declinations.isEmpty()) {
                    nounTitles.addAll(declinations);
                }

                for (final Section section : pp.getSections()) {

                    final List<Template> partOfSpeechTemplates = getPartOfSpeechTemplates(section);
                    if (!partOfSpeechTemplates.isEmpty()) {
                        for (final Template template : partOfSpeechTemplates) {
                            if (isNoun.f(getFirstParameter.f(template))) {
                                nounTitles.add(pageTitle);
                                if (declinations.isEmpty() && LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("Found no declinations for page {}", pageTitle);
                                }
                            }
                        }
                        return;
                    }
                }
                if (LOGGER.isDebugEnabled() && rev.Text.contains("Substantiv")) {
                    LOGGER.debug("No part-of-speech found for {} (which indeed contains 'Substantiv')", pageTitle);
                }
            }
        }

        private Set<String> getDeclinations(final List<Template> allTemplates) {
            final List<Template> declinationTemplates = getTemplate(allTemplates, "Deutsch_Substantiv_Übersicht");
            if (!declinationTemplates.isEmpty()) {
                if (declinationTemplates.size() > 1 && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Found more than 1 'Deutsch_Substantiv_Übersicht' template for {}", pageTitle);
                }
                final Set<String> declinations = new HashSet<String>();
                final Template template = declinationTemplates.get(0);
                for (final String param : template.getParameters()) {
                    final String[] parts = SINGULAR_PLURAL_PARAM_PATTERN.split(param, 0);
                    if (parts.length != 2 && LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                                "Have unexpected declination template/pattern for page '{}': {}\nExpected pattern: {}",
                                new Object[] { pageTitle, param, SINGULAR_PLURAL_PARAM_PATTERN.pattern() });
                    } else {
                        declinations.add(parts[1].trim());
                    }
                }
                return declinations;
            }
            return Collections.emptySet();
        }

        private List<Template> getPartOfSpeechTemplates(final Section section) {
            if (section.getTitleElement() != null) {
                final List<Template> templates = getTemplate(section.getTitleElement().getTemplates(), "Wortart");
                if (!templates.isEmpty()) {
                    return templates;
                }
            }

            final List<Content> contentList = section.getContentList();
            if (contentList != null) {
                for (final Content content : contentList) {
                    final List<Template> result = getTemplate(content.getTemplates(), "Wortart");
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            }

            return Collections.emptyList();
        }

        private List<Template> getTemplate(final List<Template> templates, final String name) {
            if (templates != null) {
                final List<Template> result = new ArrayList<Template>();
                for (final Template template : templates) {
                    if (name.equals(template.getName())) {
                        result.add(template);
                    }
                }
                return result;
            }
            return Collections.emptyList();
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
        final XmlDumpReader dumpReader = new XmlDumpReader(fis, new NamespaceFilter(new LatestFilter(
                pageTitleNounCollector), "NS_MAIN"));
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
