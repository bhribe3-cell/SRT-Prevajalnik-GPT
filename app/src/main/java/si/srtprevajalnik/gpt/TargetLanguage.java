package si.srtprevajalnik.gpt;

enum TargetLanguage {
    SLOVENIAN("sl", "Slovenščina", "Slovenian"),
    ENGLISH("en", "Angleščina", "English"),
    CROATIAN("hr", "Hrvaščina", "Croatian"),
    SERBIAN("sr", "Srbščina", "Serbian"),
    GERMAN("de", "Nemščina", "German");

    final String code, display, prompt;
    TargetLanguage(String code, String display, String prompt) { this.code = code; this.display = display; this.prompt = prompt; }
    static TargetLanguage fromCode(String code) {
        for (TargetLanguage language : values()) if (language.code.equals(code)) return language;
        throw new IllegalArgumentException("Nepodprt ciljni jezik: " + code);
    }
    @Override public String toString() { return display; }
}
