package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.QrContent;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/** The bar above the picture that shows a QR code that was found, with Open, Copy and Close. */
public final class QrBanner extends JPanel {
    private final Messages msg;
    private final JLabel kind = new JLabel();
    private final JLabel text = new JLabel();
    private final JButton open;
    private final JButton copy;

    public QrBanner(Messages msg, MainPresenter presenter) {
        super(new BorderLayout(8, 0));
        this.msg = msg;
        setName("qr-banner");
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(4, 8, 4, 6)));
        kind.setFont(kind.getFont().deriveFont(Font.BOLD));
        text.setName("qr-text");
        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.add(kind, BorderLayout.WEST);
        left.add(text, BorderLayout.CENTER);
        add(left, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        open = button("qr.open", presenter::qrOpen);
        copy = button("qr.copy", presenter::qrCopy);
        buttons.add(open);
        buttons.add(copy);
        buttons.add(button("qr.close", presenter::qrDismiss));
        add(buttons, BorderLayout.EAST);
        setVisible(false);
    }

    public void show(QrContent content) {
        kind.setText(msg.get("qr.kind." + content.kind().name().toLowerCase(java.util.Locale.ROOT)));
        String shown = switch (content.kind()) {
            case WIFI -> msg.format("qr.wifi", content.wifiName(), content.wifiPassword());
            default -> content.text();
        };
        text.setText(shown.length() > 90 ? shown.substring(0, 89) + "…" : shown);
        text.setToolTipText(content.text());
        open.setVisible(content.kind() == QrContent.Kind.LINK || content.kind() == QrContent.Kind.MAIL);
        Mnemonics.apply(copy, msg.get(content.kind() == QrContent.Kind.WIFI && !content.wifiPassword().isEmpty()
                ? "qr.copy-password" : "qr.copy"));
        setVisible(true);
        revalidate();
    }

    public String text() {
        return text.getText();
    }

    private JButton button(String key, Runnable action) {
        JButton b = Mnemonics.apply(new JButton(), msg.get(key));
        b.setName(key);
        b.addActionListener(e -> action.run());
        return b;
    }
}
