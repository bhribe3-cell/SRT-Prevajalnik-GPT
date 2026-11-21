package si.srtprevajalnik.gpt;

import java.util.Arrays;

/** Explicitly supported source languages. All translations are into Slovenian. */
final class SubtitleLanguages {
    static final String[] CODES = {"auto", "en", "hr", "de", "fr", "es", "it", "nl", "no", "sr", "bs"};
    static final String[] LABELS = {
        "Samodejno zaznaj", "Angleščina", "Hrvaščina", "Nemščina", "Francoščina",
        "Španščina", "Italijanščina", "Nizozemščina", "Norveščina",
        "Srbščina (latinica ali cirilica)", "Bosanščina (BiH)"
    };
    private static final String[] PROMPT_NAMES = {
        "Detect the source language automatically from the supplied subtitle text. It may vary between lines.",
        "English", "Croatian", "German", "French", "Spanish", "Italian",
        "Dutch", "Norwegian (Bokmål or Nynorsk)", "Serbian (Latin or Cyrillic script)", "Bosnian"
    };
    static int indexOf(String code) {
        int index = Arrays.asList(CODES).indexOf(code);
        return index < 0 ? 0 : index;
    }
    static String nameForPrompt(String code) {
        return PROMPT_NAMES[indexOf(code)];
    }
    private SubtitleLanguages() {}
}
