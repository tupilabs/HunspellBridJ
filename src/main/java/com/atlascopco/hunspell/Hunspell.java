package com.atlascopco.hunspell;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bridj.Pointer;

import com.atlascopco.hunspell.HunspellLibrary.Hunhandle;
import com.google.code.externalsorting.ExternalSort;

/**
 * This class implements an object-oriented interface to the C API for Hunspell.
 * 
 * @author Thomas Joiner
 * @author Bruno P. Kinoshita
 */
public class Hunspell implements Closeable {
	
	public static final Logger LOGGER = Logger.getLogger(Hunspell.class.getName());
	
	private Pointer<Hunhandle> handle;
	private Exception closedAt;
	
	private final Set<String> diff = new HashSet<String>();
	
	private final String dictionaryPath;
	private final String affixPath;
	
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	
	private final Charset charset;
	private final Locale locale;
	
	/**
	 * A comparator that uses locale. 
	 * @see {@link Collator}
	 */
	private static class LocaleComparator implements Comparator<String> {

		private Collator collator;

		LocaleComparator(Locale locale) {
			this.collator = Collator.getInstance(locale);
		}
		
		@Override
		public int compare(String o1, String o2) {
			return collator.compare(o1, o2);
		}
		
	}
	
	/**
	 * Instantiate a hunspell object with the given dictionary and affix file
	 * @param dictionaryPath the path to the dictionary
	 * @param affixPath the path to the affix file
	 */
	public Hunspell(String dictionaryPath, String affixPath) {
		this(dictionaryPath, affixPath, Charset.defaultCharset(), Locale.getDefault());
	}
	
	/**
	 * Instantiate a hunspell object with the given dictionary and affix file
	 * @param dictionaryPath the path to the dictionary
	 * @param affixPath the path to the affix file
	 * @param charset charset used
	 * @param locale locale used
	 */
	public Hunspell(String dictionaryPath, String affixPath, Charset charset, Locale locale) {
		this.affixPath = affixPath;
		this.dictionaryPath = dictionaryPath;
		this.charset = charset;
		this.locale = locale;
		
		Pointer<Byte> affpath = Pointer.pointerToCString(affixPath);
		Pointer<Byte> dpath = Pointer.pointerToCString(dictionaryPath);
		
		handle = HunspellLibrary.Hunspell_create(affpath, dpath);
		
		if ( this.handle == null ) {
			throw new RuntimeException("Unable to instantiate Hunspell handle.");
		}
	}
	
	/**
	 * <p>
	 * Instantiate a hunspell object with the given hunzipped dictionary and
	 * affix files.
	 * </p>
	 * 
	 * <p>
	 * This is, however more complicated than it looks. Note that the paths
	 * aren't actually to the hunzipped dictionary and affix files, they are the
	 * paths to what they would be named if they weren't hunzipped. In other
	 * words, if you have the files {@code /path/to/dictionary.dic.hz} and
	 * {@code /path/to/dictionary.aff.hz} you would call
	 * {@code new Hunspell("/path/to/dictionary.dic", "/path/to/dictionary.aff", "password")}
	 * . Note, however, that if the paths that you give actually exist, those
	 * will be prioritized over the hunzipped versions and will be used instead.
	 * </p>
	 * 
	 * @param dictionaryPath the path to the dictionary
	 * @param affixPath the path to the affix file
	 * @param key the key used to encrypt the dictionary files
	 */
	public Hunspell(String dictionaryPath, String affixPath, String key) {
		this(dictionaryPath, affixPath, key, Charset.defaultCharset(), Locale.getDefault());
	}
	
	/**
	 * <p>
	 * Instantiate a hunspell object with the given hunzipped dictionary and
	 * affix files.
	 * </p>
	 * 
	 * <p>
	 * This is, however more complicated than it looks. Note that the paths
	 * aren't actually to the hunzipped dictionary and affix files, they are the
	 * paths to what they would be named if they weren't hunzipped. In other
	 * words, if you have the files {@code /path/to/dictionary.dic.hz} and
	 * {@code /path/to/dictionary.aff.hz} you would call
	 * {@code new Hunspell("/path/to/dictionary.dic", "/path/to/dictionary.aff", "password")}
	 * . Note, however, that if the paths that you give actually exist, those
	 * will be prioritized over the hunzipped versions and will be used instead.
	 * </p>
	 * 
	 * @param dictionaryPath the path to the dictionary
	 * @param affixPath the path to the affix file
	 * @param key the key used to encrypt the dictionary files
	 * @param charset the charset used
	 * @param locale the locale used
	 */
	public Hunspell(String dictionaryPath, String affixPath, String key, Charset charset, Locale locale) {
		this.affixPath = affixPath;
		this.dictionaryPath = dictionaryPath;
		this.charset = charset;
		this.locale = locale;
		
		Pointer<Byte> affpath = Pointer.pointerToCString(affixPath);
		Pointer<Byte> dpath = Pointer.pointerToCString(dictionaryPath);
		Pointer<Byte> keyCString = Pointer.pointerToCString(key);
		
		handle = HunspellLibrary.Hunspell_create_key(affpath, dpath, keyCString);
		
		if ( this.handle == null ) {
			throw new RuntimeException("Unable to instantiate Hunspell handle.");
		}
	}
	
	/**
	 * Return diff.
	 * @return diff
	 */
	public Set<String> getDiff() {
		return diff;
	}
	
	/**
	 * Return affix path.
	 * @return affix path
	 */
	/* default */ String getAffixPath() {
		return affixPath;
	}
	
	/**
	 * Return dictionary path.
	 * @return dictionary path
	 */
	/* default */ String getDictionaryPath() {
		return dictionaryPath;
	}
	
	/**
	 * @throws RuntimeException
	 */
	public void updateDictionary() {
		if (LOGGER.isLoggable(Level.FINEST))
				LOGGER.finest("Updating dictionary " + dictionaryPath);
		
		Set<String> diff = getDiff();
		LocaleComparator comparator = new LocaleComparator(locale);
		
		RandomAccessFile dictionaryFile = null;
		FileChannel channel = null;
		
		try {
			dictionaryFile = new RandomAccessFile(dictionaryPath, "rw");
			
			channel = dictionaryFile.getChannel();
			ByteBuffer buffer = channel.map(MapMode.READ_ONLY, 0, 100);
			
			StringBuilder wordCount = new StringBuilder();
			for (int i = 0; i < buffer.remaining(); ++i) {
				char c = (char) buffer.get();
				if (c == '\n') {
					break;
				}
				wordCount.append(c);
			}
			
			long entries = Long.parseLong(wordCount.toString().trim());
			if (LOGGER.isLoggable(Level.FINEST))
				LOGGER.finest("Dictionary had " + entries + " entries. Adding new entries.");
			
			try {
				StringBuilder newEntries = new StringBuilder();
				for (String word : diff) {
					newEntries.append(word + LINE_SEPARATOR);
				}
				CharBuffer charBuffer = CharBuffer.wrap(newEntries.toString());
				channel.position(channel.size());
				channel.write(charset.encode(charBuffer));
			} catch (IOException e) {
				throw new RuntimeException("Failed to update dictionary " + dictionaryPath, e);
			} 
			
			entries += diff.size();
			if (LOGGER.isLoggable(Level.FINEST))
				LOGGER.finest("Dictionary updating number of entries to " + entries);
			channel.position(0);
			channel.write(ByteBuffer.wrap((""+entries).getBytes()));
			
			if (LOGGER.isLoggable(Level.FINEST))
				LOGGER.finest("Sorting dictionary " + dictionaryPath);
			
		} catch (FileNotFoundException e2) {
			throw new RuntimeException("Failed to open dictionary " + dictionaryPath, e2);
		} catch (IOException e1) {
			throw new RuntimeException("Failed to open dictionary " + dictionaryPath, e1);
		} finally {
			if (null != channel) {
				try {
					channel.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (null != dictionaryFile) {
				try {
					dictionaryFile.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// FIXME: fix encoding problem while merging files
		File input = new File(dictionaryPath);
		File output = new File(dictionaryPath);
		try {
			ExternalSort.mergeSortedFiles(
					ExternalSort.sortInBatch(
							input,
							comparator, 
							ExternalSort.DEFAULTMAXTEMPFILES, 
							charset,
							null, 
							true, 
							1, 
							false), 
					output, 
					comparator, 
					charset, 
					true, 
					false, 
					false);
		} catch (IOException e) {
			throw new RuntimeException("Failed to sort dictionary "
					+ dictionaryPath, e);
		}
		
		if (LOGGER.isLoggable(Level.FINEST))
			LOGGER.finest("Dictionary " + dictionaryPath + " updated!");
	}

	/**
	 * Spellcheck the given word.
	 * @param word the word to check
	 * @return true if it is spelled correctly
	 * @see HunspellLibrary#Hunspell_spell(Pointer, Pointer)
	 */
	public boolean spell(String word) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		
		int result = HunspellLibrary.Hunspell_spell(handle, wordCString);
		
		return result != 0;
	}
	
	/**
	 * Same as {@link #spell(String)}
	 * @param word the word to check.
	 * @return true if it correct
	 * @see #spell(String)
	 */
	public boolean isCorrect(String word) {
		return spell(word);
	}

	/**
	 * Get the dictionary encoding for this object.
	 * @return the encoding for the dictionary
	 * @see HunspellLibrary#Hunspell_get_dic_encoding(Pointer)
	 */
	public String getDictionaryEncoding() {
		// check handle before attempting to operate on
		checkHandle();
		
		Pointer<Byte> dictionaryEncoding = HunspellLibrary.Hunspell_get_dic_encoding(handle);
		
		return dictionaryEncoding.getCString();
	}

	/**
	 * Suggest a list of corrections for the given word.
	 * @param word the word to get suggestions for
	 * @return the list of suggestions
	 * @see HunspellLibrary#Hunspell_suggest(Pointer, Pointer, Pointer)
	 */
	public List<String> suggest(String word) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		Pointer<Pointer<Pointer<Byte>>> slst = Pointer.allocatePointerPointer(Byte.class);
		
		int numResults = 0;
		
		List<String> suggestions = new ArrayList<String>();
		try {
			numResults = HunspellLibrary.Hunspell_suggest(handle, slst, wordCString);
		
			for ( int i = 0; i < numResults; i++) {
				suggestions.add(slst.get().get(i).getCString());
			}
		} finally {
			if ( slst != null ) {
				this.free_list(slst, numResults);
			}
		}
		
		return suggestions;
	}

	/**
	 * Morphological analysis of the given word.
	 * @param word the word to analyze
	 * @return the analysis
	 * @see HunspellLibrary#Hunspell_analyze(Pointer, Pointer, Pointer)
	 */
	public List<String> analyze(String word) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		Pointer<Pointer<Pointer<Byte>>> slst = Pointer.allocatePointerPointer(Byte.class);
		
		int numResults = 0;
		
		List<String> suggestions = new ArrayList<String>();
		try {
			numResults = HunspellLibrary.Hunspell_analyze(handle, slst, wordCString);
		
			for ( int i = 0; i < numResults; i++) {
				suggestions.add(slst.get().get(i).getCString());
			}
		} finally {
			if ( slst != null ) {
				this.free_list(slst, numResults);
			}
		}
		
		return suggestions;
	}

	/**
	 * Gets the stems of the word.
	 * @param word the word
	 * @return stems for the word
	 * @see HunspellLibrary#Hunspell_stem(Pointer, Pointer, Pointer)
	 */
	public List<String> stem(String word) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		Pointer<Pointer<Pointer<Byte>>> slst = Pointer.allocatePointerPointer(Byte.class);
		
		int numResults = 0;
		
		List<String> suggestions = new ArrayList<String>();
		try {
			numResults = HunspellLibrary.Hunspell_stem(handle, slst, wordCString);
		
			for ( int i = 0; i < numResults; i++) {
				suggestions.add(slst.get().get(i).getCString());
			}
		} finally {
			if ( slst != null ) {
				this.free_list(slst, numResults);
			}
		}
		
		return suggestions;
	}

	/**
	 * Gets the stems of a word from the results of {@link #analyze(String)}.
	 * @param analysis the results of {@link #analyze(String)}
	 * @return the stem information
	 * @see HunspellLibrary#Hunspell_stem2(Pointer, Pointer, Pointer, int)
	 */
	public List<String> stem(List<String> analysis) {
		// check handle before attempting to operate on
		checkHandle();
		
		Pointer<Pointer<Pointer<Byte>>> slst = Pointer.allocatePointerPointer(Byte.class);
		Pointer<Pointer<Byte>> analysisCStrings = Pointer.pointerToCStrings(analysis.toArray(new String[analysis.size()]));
		
		int numResults = 0;
		
		List<String> suggestions = new ArrayList<String>();
		try {
			numResults = HunspellLibrary.Hunspell_stem2(handle, slst, analysisCStrings, analysis.size());
		
			for ( int i = 0; i < numResults; i++) {
				suggestions.add(slst.get().get(i).getCString());
			}
		} finally {
			if ( slst != null ) {
				this.free_list(slst, numResults);
			}
		}
		
		return suggestions;
	}

	/**
	 * Generate a form for the first word based on the second word.
	 * @param word the word to generate the form for
	 * @param basedOn the word to base the generation on
	 * @return the generated form
	 * @see HunspellLibrary#Hunspell_generate(Pointer, Pointer, Pointer, Pointer)
	 */
	public List<String> generate(String word, String basedOn) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		checkWord("basedOn", basedOn);
		
		Pointer<Pointer<Pointer<Byte>>> slst = Pointer.allocatePointerPointer(Byte.class);
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		Pointer<Byte> word2CString = Pointer.pointerToCString(basedOn);
		
		int numResults = 0;
		
		List<String> suggestions = new ArrayList<String>();
		try {
			numResults = HunspellLibrary.Hunspell_generate(handle, slst, wordCString, word2CString);
		
			for ( int i = 0; i < numResults; i++) {
				suggestions.add(slst.get().get(i).getCString());
			}
		} finally {
			if ( slst != null ) {
				this.free_list(slst, numResults);
			}
		}
		
		return suggestions;
	}

	/**
	 * Generate a form for the given word based on the analysis of a second word.
	 * @param word the word for which to generate the form
	 * @param basedOnAnalysis the analysis of the word that it is based on
	 * @return the generated form(s)
	 * @see HunspellLibrary#Hunspell_generate2(Pointer, Pointer, Pointer, Pointer, int)
	 */
	public List<String> generate(String word, List<String> basedOnAnalysis) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		
		Pointer<Pointer<Pointer<Byte>>> slst = Pointer.allocatePointerPointer(Byte.class);
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		Pointer<Pointer<Byte>> analysisCStrings = Pointer.pointerToCStrings(basedOnAnalysis.toArray(new String[basedOnAnalysis.size()]));
		
		int numResults = 0;
		
		List<String> suggestions = new ArrayList<String>();
		try {
			numResults = HunspellLibrary.Hunspell_generate2(handle, slst, wordCString, analysisCStrings, basedOnAnalysis.size());
		
			for ( int i = 0; i < numResults; i++) {
				suggestions.add(slst.get().get(i).getCString());
			}
		} finally {
			if ( slst != null ) {
				this.free_list(slst, numResults);
			}
		}
		
		return suggestions;
	}

	/**
	 * Add a word to the runtime dictionary.
	 * @param word the word to add
	 * @see HunspellLibrary#Hunspell_add(Pointer, Pointer)
	 */
	public void add(String word) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		
		int result = HunspellLibrary.Hunspell_add(handle, wordCString);

		if ( result != 0 ) {
			throw new RuntimeException("An error occurred when calling Hunspell_add: "+result);
		}
		
		this.diff.add(word);
	}

	/**
	 * Add the word to the runtime dictionary with the affix flags of the given
	 * example word so that affixed versions will be recognized as well.
	 * 
	 * @param word the word
	 * @param exampleWord a word that shows an example of what affix rules apply
	 * @see HunspellLibrary#Hunspell_add_with_affix(Pointer, Pointer, Pointer)
	 */
	public void addWithAffix(String word, String exampleWord) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		checkWord("exampleWord", exampleWord);
		
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		Pointer<Byte> example = Pointer.pointerToCString(exampleWord);
		
		int result = HunspellLibrary.Hunspell_add_with_affix(handle, wordCString, example);
		
		if ( result != 0 ) {
			throw new RuntimeException("An error occurred when calling Hunspell_add_with_affix: "+result);
		}
		
		this.diff.add(word);
	}

	/**
	 * Remove a word from the runtime dictionary.
	 * 
	 * @param word the word to remove
	 * @see HunspellLibrary#Hunspell_remove(Pointer, Pointer)
	 */
	public void remove(String word) {
		// check handle before attempting to operate on
		checkHandle();
		checkWord("word", word);
		
		Pointer<Byte> wordCString = Pointer.pointerToCString(word);
		
		int result = HunspellLibrary.Hunspell_remove(handle, wordCString);
		
		if ( result != 0 ) {
			throw new RuntimeException("An error occurred when calling Hunspell_remove: "+result);
		}
	}

	/**
	 * This method frees a list that Hunspell allocated.
	 * @param slst the list that hunspell allocated
	 * @param n the number of items in the list
	 * @see HunspellLibrary#Hunspell_free_list(Pointer, Pointer, int)
	 */
	private void free_list(Pointer<Pointer<Pointer<Byte>>> slst, int n) {
		HunspellLibrary.Hunspell_free_list(handle, slst, n);
	}
	
	/**
	 * Ensures the given word is not too long for the library to handle it
	 * @param parameterName the name of the parameter (for the error message)
	 * @param value the value of the parameter
	 */
	private void checkWord(String parameterName, String value) {
		if ( value.length() > HunspellLibrary.MAXWORDUTF8LEN ) {
			throw new IllegalArgumentException("Word '"+parameterName+"' greater than max acceptable length ("+HunspellLibrary.MAXWORDUTF8LEN+"): "+value);
		}
	}
	
	/**
	 * Checks the handle to make sure that it is still non-null.
	 */
	private void checkHandle() {
		if ( this.handle == null && this.closedAt != null ) {
			throw new IllegalStateException("This instance has already been closed.", closedAt);
		} else if ( this.handle == null ) {
			throw new IllegalStateException("Hunspell handle is null, but instance has not been closed.");
		}
	}

	/**
	 * This method will handle the destruction of the Hunspell instance and
	 * ensure that the memory is reclaimed.
	 */
	@Override
	public void close() {
		// Don't attempt to close multiple times
		if ( this.closedAt != null ) {
			return;
		}
		
		// Just in case the user has been messing with what they shouldn't
		if ( this.handle != null ) {
			HunspellLibrary.Hunspell_destroy(handle);
		} else {
			return;
		}
		
		this.handle = null;
		this.closedAt = new Exception();
	}
	
	@Override
	protected void finalize() throws Throwable {
		if (this.closedAt!=null){
			this.close();
			System.err.println("Hunspell instance was not closed!");
		}
		
		super.finalize();
	}

}
