package com.atlascopco.hunspell;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

public class T {

	public static void main(String[] args) {
		ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.FINEST);
        Hunspell.LOGGER.setLevel(Level.FINEST);
        Hunspell.LOGGER.addHandler(ch);
        
		Hunspell dict = new Hunspell("/home/kinow/Desktop/pt_BR.dic", "/home/kinow/Desktop/pt_BR.aff", Charset.forName("ISO-8859-1"), new Locale("pt", "BR"));
		String word = "borogodó";
		if (!dict.spell(word)) {
			System.out.printf("The word %s is not valid!\n", word);
			dict.add(word);
		}
		if (dict.spell(word)) {
			System.out.printf("Now the word %s is valid!\n", word);
		}
		if (dict.spell("307375")) {
			System.out.println("BUG!");
		}
		dict.updateDictionary(); // 307374
		dict.close();
	}
	
}
