An in-memory, work-in-progress java api for wiktionary.org.

Right now the only feature is that you can check if a (german) word is a noun (based on a dump of de.wiktionary.org).

# Usage
1. Add the artifact to your classpath
2. Get the dump from http://dumps.wikimedia.org/dewiktionary/latest/dewiktionary-latest-pages-articles.xml.bz2
3. Unpack the dump (`bzip2 -d dewiktionary-latest-pages-articles.xml.bz2`)
4. Create a new WiktionaryLoader instance, passing the unpacked file:

<pre><code>final WiktionaryLoader loader = new WiktionaryLoader(
    new File("dewiktionary-latest-pages-articles.xml"));
// Check if a word is a noun:
System.out.println("Is noun" + loader.isNoun("Januar"));
</code></pre>