import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SimpleWindow extends JFrame {
    private JLabel messageLabel;
    private JTextField inputField;

    public SimpleWindow() {
        super("示例 Java 窗口");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 220);
        setLocationRelativeTo(null); // 居中

        // 菜单栏
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // 主面板
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        messageLabel = new JLabel("请输入文本并点击按钮：");
        panel.add(messageLabel, BorderLayout.NORTH);

        inputField = new JTextField();
        panel.add(inputField, BorderLayout.CENTER);

        JButton button = new JButton("显示文本");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = inputField.getText().trim();
                if (text.isEmpty()) {
                    JOptionPane.showMessageDialog(SimpleWindow.this, "请输入一些文本。", "提示", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    messageLabel.setText("你输入的是: " + text);
                }
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(button);
        panel.add(bottom, BorderLayout.SOUTH);

        add(panel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimpleWindow w = new SimpleWindow();
            w.setVisible(true);
        });
    }
}
