/**
 * Configuració d'un test concret. Cada test té la seva pròpia instància amb el seu títol, carpeta d'imatges i fitxer xml de preguntes.
 */

public class QuizConfig {
    public final String id;
    public final String title;
    public final String baseDir;
    public final String xmlFile;
    public final int numQuestions;
    public final int maxTimeMinutes;

    public QuizConfig(String id, String title, String baseDir, String xmlFile, int numQuestions, int maxTimeMinutes) {
        this.id = id;
        this.title = title;
        this.baseDir = baseDir;
        this.xmlFile = xmlFile;
        this.numQuestions = numQuestions;
        this.maxTimeMinutes = maxTimeMinutes;
    }

    public String xmlPath() {
        return baseDir + xmlFile;
    }

    public String progressFile() {
        return "progress_" + id + ".txt";
    }
}