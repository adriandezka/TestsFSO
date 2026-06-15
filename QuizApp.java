import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class QuizApp extends JFrame {

    // ── Configuració d'aquest test ────────────────────────────────────
    private final QuizConfig config;
    private final Runnable onFinish; // callback opcional (mode "tots seqüencial")

    // ── State ───────────────────────────────────────────────────────
    private List<Question> questions;
    private int currentQuestion;
    private boolean questionAnswered = false;
    private boolean fin = false;

    private int correctCount = 0;
    private int incorrectCount = 0;
    private double puntuacio = 0.0;

    private long startTimeMillis;

    // ── UI components ───────────────────────────────────────────────
    private JTextPane labelQuestion;
    private JLabel imageLabel;
    private ButtonGroup radioGroup;
    private List<JRadioButton> radioButtons;
    private JButton nextButton;
    private JLabel statsLabel;
    private JLabel timerLabel;
    private javax.swing.Timer swingTimer;

    // ── Data class ──────────────────────────────────────────────────
    static class Question {
        String text;
        List<String> options;
        String correctAnswer; // "a", "b", "c", …
        String imagePath;     // null if none

        Question(String text, List<String> options, String correctAnswer, String imagePath) {
            this.text = text;
            this.options = options;
            this.correctAnswer = correctAnswer;
            this.imagePath = imagePath;
        }
    }

    // ── Constructor ─────────────────────────────────────────────────
    /**
     * @param config   configuració del test (títol, carpeta, NUM_QUESTIONS, temps...)
     * @param onFinish callback executat quan el test acaba de forma normal
     *                  (útil per al mode "tots seqüencial"); pot ser null.
     */
    public QuizApp(QuizConfig config, Runnable onFinish) {
        super(config.title);
        this.config = config;
        this.onFinish = onFinish;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClosing(); }
        });

        questions = loadQuestionsFromXml(config.xmlPath());
        shuffleQuestions();
        currentQuestion = loadProgress();

        buildUI();
        centerWindow();
        setVisible(true);

        startTimeMillis = System.currentTimeMillis();
        startTimer();
        updateStatsLabel();
        loadQuestion();
    }

    // ── UI Construction ─────────────────────────────────────────────
    private void buildUI() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(new EmptyBorder(10, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // rows do NOT stretch vertically by default
        gbc.insets = new Insets(4, 0, 4, 0);

        // Question text pane — wraps naturally to panel width, height = preferred only
        labelQuestion = new JTextPane();
        labelQuestion.setContentType("text/html");
        labelQuestion.setEditable(false);
        labelQuestion.setOpaque(false);
        labelQuestion.setFocusable(false);
        gbc.gridy = 0;
        mainPanel.add(labelQuestion, gbc);

        // Image label
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        mainPanel.add(imageLabel, gbc);

        // Radio buttons (max options across all questions)
        int maxOptions = questions.stream().mapToInt(q -> q.options.size()).max().orElse(4);
        radioGroup = new ButtonGroup();
        radioButtons = new ArrayList<>();
        for (int i = 0; i < maxOptions; i++) {
            JRadioButton rb = new JRadioButton("");
            rb.setFont(new Font("Arial", Font.PLAIN, 18));
            rb.setHorizontalAlignment(SwingConstants.CENTER);
            radioGroup.add(rb);
            radioButtons.add(rb);
            gbc.gridy = 2 + i;
            mainPanel.add(rb, gbc);
            final int idx = i;
            rb.addActionListener(e -> checkAnswer(idx));
        }

        // Next button
        nextButton = new JButton("Següent");
        nextButton.setFont(new Font("Arial", Font.BOLD, 18));
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> nextQuestion());
        gbc.gridy = 2 + maxOptions;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(nextButton, gbc);

        // Vertical filler — this row gets all the extra space, pushing content up
        gbc.gridy = 3 + maxOptions;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weighty = 1.0;
        mainPanel.add(Box.createGlue(), gbc);

        // Bottom panel for stats & timer
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1));
        statsLabel = new JLabel("", SwingConstants.CENTER);
        statsLabel.setFont(new Font("Arial", Font.BOLD, 17));
        timerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 17));
        bottomPanel.add(timerLabel);
        bottomPanel.add(statsLabel);

        add(mainPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        setSize(1300, 620);
    }

    private void centerWindow() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }

    // ── Timer ────────────────────────────────────────────────────────
    private void startTimer() {
        swingTimer = new javax.swing.Timer(1000, e -> updateTimer());
        swingTimer.start();
    }

    private void updateTimer() {
        if (fin) return;
        long elapsedSec = (System.currentTimeMillis() - startTimeMillis) / 1000;
        long elapsedMin = elapsedSec / 60;
        long elapsedSecRem = elapsedSec % 60;

        long timeToFinishMin = config.maxTimeMinutes - elapsedMin - 1;
        long timeToFinishSec = 59 - elapsedSecRem;

        timerLabel.setText(String.format("Temps restant: %d min %02d seg", timeToFinishMin, timeToFinishSec));

        if (timeToFinishMin == 0 && timeToFinishSec == 0) {
            fiTest();
        }
    }

    // ── Progress persistence ─────────────────────────────────────────
    private int loadProgress() {
        try {
            String content = Files.readString(Path.of(config.progressFile())).trim();
            return Integer.parseInt(content);
        } catch (Exception e) {
            saveProgress(1);
            return 1;
        }
    }

    private void saveProgress(int progress) {
        try {
            Files.writeString(Path.of(config.progressFile()), String.valueOf(progress));
        } catch (IOException e) {
            System.err.println("Could not save progress: " + e.getMessage());
        }
    }

    private void deleteProgressFile() {
        try { Files.deleteIfExists(Path.of(config.progressFile())); }
        catch (IOException ignored) {}
    }

    // ── XML Loading ──────────────────────────────────────────────────
    private List<Question> loadQuestionsFromXml(String filePath) {
        List<Question> list = new ArrayList<>();
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new File(filePath));
            NodeList preguntaNodes = doc.getElementsByTagName("pregunta");

            for (int i = 0; i < preguntaNodes.getLength(); i++) {
                Element pregunta = (Element) preguntaNodes.item(i);
                String text = pregunta.getElementsByTagName("texto").item(0).getTextContent();
                String correctAnswer = pregunta.getElementsByTagName("respuesta_correcta").item(0).getTextContent().trim();

                List<String> options = new ArrayList<>();
                NodeList opciones = pregunta.getElementsByTagName("opcion");
                for (int j = 0; j < opciones.getLength(); j++) {
                    options.add(opciones.item(j).getTextContent());
                }

                String imagePath = null;
                NodeList imageNodes = pregunta.getElementsByTagName("imagen");
                if (imageNodes.getLength() > 0) {
                    String img = imageNodes.item(0).getTextContent().trim();
                    if (!img.isEmpty()) imagePath = img;
                }

                list.add(new Question(text, options, correctAnswer, imagePath));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading XML: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return list;
    }

    private void shuffleQuestions() {
        Collections.shuffle(questions);
    }

    // ── Question Display ─────────────────────────────────────────────
    private void loadQuestion() {
        if (currentQuestion <= config.numQuestions) {
            questionAnswered = false;
            Question q = questions.get(currentQuestion);

            // Set question text with centered styling, font size adapts to panel width automatically
            labelQuestion.setText("<html><body style='font-family:Arial; font-size:18pt; font-weight:bold; text-align:center;'>"
                + escapeHtml(q.text) + "</body></html>");

            // Image (resolt relatiu a la carpeta del test, no al directori de treball)
            if (q.imagePath != null) {
                try {
                    BufferedImage img = ImageIO.read(new File(config.baseDir, q.imagePath));
                    int maxW = 500, maxH = 375;
                    double scale = Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight());
                    int newW = (int)(img.getWidth() * scale);
                    int newH = (int)(img.getHeight() * scale);
                    Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                    imageLabel.setIcon(new ImageIcon(scaled));
                    imageLabel.setVisible(true);
                } catch (Exception e) {
                    System.err.println("Error loading image: " + e.getMessage());
                    imageLabel.setVisible(false);
                }
            } else {
                imageLabel.setIcon(null);
                imageLabel.setVisible(false);
            }

            // Radio buttons
            radioGroup.clearSelection();
            for (int i = 0; i < radioButtons.size(); i++) {
                JRadioButton rb = radioButtons.get(i);
                if (i < q.options.size()) {
                    rb.setText(q.options.get(i));
                    rb.setForeground(Color.BLACK);
                    rb.setEnabled(true);
                    rb.setVisible(true);
                } else {
                    rb.setVisible(false);
                }
            }

            nextButton.setEnabled(false);
        } else {
            fiTest();
        }
    }

    // ── Answer Checking ───────────────────────────────────────────────
    private void checkAnswer(int selectedIndex) {
        if (questionAnswered) return;
        questionAnswered = true;

        Question q = questions.get(currentQuestion);
        String userAnswer = String.valueOf((char)('a' + selectedIndex));
        boolean correct = userAnswer.equals(q.correctAnswer);

        if (correct) {
            radioButtons.get(selectedIndex).setForeground(new Color(0, 150, 0));
            correctCount++;
            JOptionPane.showMessageDialog(this, "Correcte!", "Resposta Correcta", JOptionPane.INFORMATION_MESSAGE);
        } else {
            radioButtons.get(selectedIndex).setForeground(Color.RED);
            incorrectCount++;
            JOptionPane.showMessageDialog(this,
                "Incorrecte! Resposta correcta: " + q.correctAnswer,
                "Resposta Incorrecta", JOptionPane.INFORMATION_MESSAGE);
        }

        // Disable all options
        for (int i = 0; i < q.options.size(); i++) {
            radioButtons.get(i).setEnabled(false);
        }

        nextButton.setEnabled(true);
        updateStatsLabel();
    }

    private void nextQuestion() {
        currentQuestion++;
        saveProgress(currentQuestion);
        loadQuestion();
    }

    // ── Stats ─────────────────────────────────────────────────────────
    private void updateStatsLabel() {
        puntuacio = ((correctCount - incorrectCount * 0.33) / config.numQuestions) * 10.0;
        statsLabel.setText(String.format(
            "Preguntes carregades: %d  |  A realitzar: %d  |  Correctes: %d  |  Incorrectes: %d  |  Puntuació: %.2f",
            questions.size(), config.numQuestions, correctCount, incorrectCount, puntuacio));
    }

    // ── End of Test ───────────────────────────────────────────────────
    private void fiTest() {
        fin = true;
        if (swingTimer != null) swingTimer.stop();
        long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
        long min = elapsed / 60, sec = elapsed % 60;
        JOptionPane.showMessageDialog(this,
            String.format("Has completat el test en %d min %02d seg!\nPuntuació final: %.2f", min, sec, puntuacio),
            "Fi del test", JOptionPane.INFORMATION_MESSAGE);
        deleteProgressFile();
        dispose();
        if (onFinish != null) onFinish.run();
    }

    private void onClosing() {
        int result = JOptionPane.showConfirmDialog(this,
            "Estàs segur que vols tancar l'aplicació?", "Tancar",
            JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            deleteProgressFile();
            if (swingTimer != null) swingTimer.stop();
            dispose();
            // Nota: si es tanca manualment NO s'invoca onFinish, de manera que
            // en mode "tots seqüencial" tancar una finestra atura tota la seqüència.
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}