package com.yonwi.farming;

import static com.yonwi.farming.PanelWidgets.CARD_TEXT;
import static com.yonwi.farming.PanelWidgets.column;
import static com.yonwi.farming.PanelWidgets.fixHeight;
import static com.yonwi.farming.PanelWidgets.html;
import static com.yonwi.farming.PanelWidgets.label;
import static com.yonwi.farming.PanelWidgets.muted;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The card at the top of the Run tab: the next run, what to withdraw, or the step to do now. */
final class GuideCard {
  private final ItemPictures pictures;
  private final JPanel card = Style.card();

  GuideCard(ItemPictures pictures) {
    this.pictures = pictures;
  }

  JPanel card() {
    return card;
  }

  /** Just a line, e.g. "All patches checked". */
  void showMessage(String text) {
    card.removeAll();
    card.add(muted(text));
    fixHeight(card);
  }

  /**
   * Between runs: nothing is guided or highlighted until you choose to start preparing a run. A
   * picture of what's growing beside the heading and when it'll be ready.
   */
  void showNextRun(String idle, String growingSapling, Runnable prepare) {
    card.removeAll();
    JLabel picture = new JLabel();
    picture.setPreferredSize(new Dimension(34, 32));
    if (growingSapling != null) pictures.small(picture, growingSapling, 34, 30);
    JPanel words = column();
    words.setOpaque(false);
    JLabel next = Style.section("Next run");
    next.setBorder(new EmptyBorder(0, 0, 3, 0));
    words.add(next);
    JLabel when =
        label(
            "<html><div style=\"width:" + (CARD_TEXT - 42) + "px\">"
                + html(idle == null ? "Press Prepare run when you're ready." : idle)
                + "</div></html>");
    when.setFont(Style.small());
    words.add(when);
    JPanel top = new JPanel(new BorderLayout(8, 0));
    top.setOpaque(false);
    top.add(picture, BorderLayout.WEST);
    top.add(words, BorderLayout.CENTER);
    top.setAlignmentX(Component.LEFT_ALIGNMENT);
    card.add(top);
    card.add(Box.createVerticalStrut(8));
    JButton button = Style.primary("Prepare run", null);
    button.setToolTipText("Show the bank kit check, guide and highlights for this run");
    button.addActionListener(e -> prepare.run());
    card.add(button);
    fixHeight(card);
  }

  /** Bank first: no teleport is guided or highlighted until the kit is carried. */
  void showWithdraw(List<String> withdraw, Runnable setOffAnyway) {
    card.removeAll();
    JLabel now = Style.section("Withdraw from the bank");
    now.setBorder(new EmptyBorder(0, 0, 3, 0));
    card.add(now);
    for (String item : withdraw) {
      JLabel line = muted("• " + item);
      line.setForeground(PanelWidgets.WARN);
      card.add(line);
    }
    card.add(Box.createVerticalStrut(6));
    JButton anyway = new JButton("Set off anyway");
    anyway.setToolTipText("Skip the kit check until this run is cleared");
    anyway.addActionListener(e -> setOffAnyway.run());
    card.add(anyway);
    fixHeight(card);
  }

  /** The step being guided, with Back, Next and Done. */
  void showStep(Guidance guide, int patches, Runnable back, Runnable next, Runnable done) {
    card.removeAll();
    JLabel now = Style.section("Now · patch " + guide.step + " of " + patches);
    now.setBorder(new EmptyBorder(0, 0, 3, 0));
    card.add(now);
    JLabel heading =
        label(
            "<html><div style=\"width:" + CARD_TEXT + "px\">"
                + html(guide.destination)
                // Travelling: the grey line below already says how to get there.
                + (guide.phase.equals("Travel") ? "" : " · " + html(guide.labels.get(guide.phaseIndex)))
                + "</div></html>");
    heading.setFont(Style.bold());
    card.add(heading);
    card.add(muted(guide.action));
    card.add(Box.createVerticalStrut(6));
    JPanel actions = new JPanel(new GridLayout(1, 3, 3, 0));
    actions.setOpaque(false);
    JButton backButton = new JButton("Back");
    JButton nextButton = new JButton("Next");
    JButton doneButton = new JButton("Done");
    backButton.setToolTipText("Previous action");
    nextButton.setToolTipText("Next action");
    doneButton.setToolTipText("Mark this patch complete and guide the next patch");
    backButton.setEnabled(guide.phaseIndex > 0);
    nextButton.setEnabled(guide.phaseIndex < guide.phases.size() - 1);
    backButton.addActionListener(e -> back.run());
    nextButton.addActionListener(e -> next.run());
    doneButton.addActionListener(e -> done.run());
    actions.add(backButton);
    actions.add(nextButton);
    actions.add(doneButton);
    fixHeight(actions);
    card.add(actions);
    fixHeight(card);
  }
}
