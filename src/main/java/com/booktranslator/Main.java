package com.booktranslator;

import java.io.IOException;

/**
 * Entry point for the Book Translator CLI.
 * Usage: java -jar book-translator.jar <input_file> <output_file> <source_lang> <target_lang>
 */
public class Main {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: java -jar book-translator.jar <input_file> <output_file> <source_lang> <target_lang>");
            System.err.println("Example: java -jar book-translator.jar book.txt translated.txt en es");
            System.exit(1);
        }

        String inputFile  = args[0];
        String outputFile = args[1];
        String sourceLang = args[2];
        String targetLang = args[3];

        System.out.printf("Translating '%s' from [%s] to [%s]...%n", inputFile, sourceLang, targetLang);

        try {
            String bookText = FileHandler.readFile(inputFile);
            System.out.printf("Read %d characters from input file.%n", bookText.length());

            TranslationService service = new TranslationService(sourceLang, targetLang);
            String translated = service.translate(bookText);

            FileHandler.writeFile(outputFile, translated);
            System.out.printf("Translation complete. Output written to '%s'.%n", outputFile);

        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
            System.exit(1);
        } catch (TranslationService.TranslationException e) {
            System.err.println("Translation error: " + e.getMessage());
            System.exit(1);
        }
    }
}
