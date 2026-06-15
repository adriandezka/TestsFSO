import javax.swing.*;
import java.util.*;

public class Launcher {
    private static final List<QuizConfig> QUIZZES = List.of(
        new QuizConfig("fso1", "Test d'examen 1 (FSO)", "preguntes/test1/", "preguntas.xml", 20, 30),
        new QuizConfig("fso2", "Test d'examen 2 (FSO)", "preguntes/test2/", "preguntas.xml", 20, 30),
        new QuizConfig("fso3", "Test d'examen 3 (FSO)", "preguntes/test3/", "preguntas.xml", 20, 30)
    );
 
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Launcher::showMenu);
    }
 
    private static void showMenu() {
        String[] options = new String[QUIZZES.size()];
        for (int i = 0; i < QUIZZES.size(); i++) {
            options[i] = QUIZZES.get(i).title;
        }
 
        int choice = JOptionPane.showOptionDialog(
            null,
            "Selecciona el test a realitzar:",
            "Selecció de test",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );
 
        if (choice == JOptionPane.CLOSED_OPTION) {
            return; // l'usuari ha tancat el diàleg sense triar
        }
        new QuizApp(QUIZZES.get(choice), null);
    }
}
