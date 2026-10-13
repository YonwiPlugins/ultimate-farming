package com.yonwi.farming;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/** Small hand-drawn icons for the sidebar tabs and the Records stats, sized for the RS font. */
final class Icons {
  private static final Color INK = new Color(225, 225, 225),
      GOLD = new Color(232, 186, 72),
      GREEN = new Color(120, 200, 90);

  private Icons() {}

  static ImageIcon get(String name) {
    int s = 16;
    BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    switch (name) {
      case "run": // a running figure
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(9, 1, 3.6, 3.6));
        g.draw(new Line2D.Double(9.5, 5.5, 7.5, 10)); // body
        g.draw(new Line2D.Double(9, 6.5, 12.5, 8.5)); // front arm
        g.draw(new Line2D.Double(9, 6.5, 5.5, 7)); // back arm
        g.draw(new Line2D.Double(7.5, 10, 10.5, 12.5)); // front leg
        g.draw(new Line2D.Double(10.5, 12.5, 10, 15));
        g.draw(new Line2D.Double(7.5, 10, 5, 12.5)); // back leg
        g.draw(new Line2D.Double(5, 12.5, 2.5, 12.5));
        break;
      case "kit": // a backpack
        g.setColor(INK);
        g.draw(new RoundRectangle2D.Double(3, 4, 10, 11, 4, 4));
        g.draw(new Arc2D.Double(5.5, 1, 5, 5, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(5.5, 9, 10.5, 9));
        break;
      case "setup": // a cog
        g.setColor(INK);
        for (int i = 0; i < 8; i++) {
          double a = Math.PI * i / 4;
          g.draw(
              new Line2D.Double(
                  8 + 5 * Math.cos(a), 8 + 5 * Math.sin(a), 8 + 7 * Math.cos(a),
                  8 + 7 * Math.sin(a)));
        }
        g.draw(new Ellipse2D.Double(3.5, 3.5, 9, 9));
        g.draw(new Ellipse2D.Double(6.2, 6.2, 3.6, 3.6));
        break;
      case "records":
      case "runs": // bar chart
        g.setColor("records".equals(name) ? GOLD : INK);
        g.fill(new Rectangle2D.Double(2, 9, 3, 6));
        g.fill(new Rectangle2D.Double(6.5, 5, 3, 10));
        g.fill(new Rectangle2D.Double(11, 1.5, 3, 13.5));
        break;
      case "time": // clock
        g.setColor(INK);
        g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
        g.draw(new Line2D.Double(8, 8, 8, 4));
        g.draw(new Line2D.Double(8, 8, 11, 9.5));
        break;
      case "herbs": // leaf
        g.setColor(GREEN);
        Path2D leaf = new Path2D.Double();
        leaf.moveTo(2, 14);
        leaf.curveTo(1, 6, 6, 1.5, 14.5, 1.5);
        leaf.curveTo(14.5, 9, 10, 14.5, 2, 14);
        g.fill(leaf);
        g.setColor(new Color(40, 90, 40));
        g.draw(new Line2D.Double(2.5, 13.5, 12, 4));
        break;
      case "average": // sigma
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString("Σ", 2, 14);
        break;
      case "profit": // stacked coins
        g.setColor(GOLD);
        for (int i = 0; i < 3; i++) {
          g.fill(new Ellipse2D.Double(2 + i * 2, 10 - i * 3.5, 9, 4.5));
          g.setColor(new Color(170, 125, 40));
          g.draw(new Ellipse2D.Double(2 + i * 2, 10 - i * 3.5, 9, 4.5));
          g.setColor(GOLD);
        }
        break;
      case "perRun": // one coin, face on (stacked coins are the total)
        g.setColor(GOLD);
        g.fill(new Ellipse2D.Double(2, 2, 12, 12));
        g.setColor(new Color(170, 125, 40));
        g.draw(new Ellipse2D.Double(2, 2, 12, 12));
        g.draw(new Ellipse2D.Double(5, 5, 6, 6));
        break;
      case "rate": // money sack
        g.setColor(new Color(190, 150, 90));
        g.fill(new Ellipse2D.Double(2, 5, 12, 10.5));
        g.fill(new Polygon(new int[] {5, 11, 9, 7}, new int[] {2, 2, 6, 6}, 4));
        g.setColor(new Color(110, 80, 40));
        g.draw(new Line2D.Double(5.5, 6, 10.5, 6));
        g.setColor(GOLD);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        g.drawString("$", 6f, 13.5f);
        break;
      case "rank": // trophy
        g.setColor(GOLD);
        g.fill(new Arc2D.Double(3.5, -1, 9, 11, 180, 180, Arc2D.CHORD));
        g.draw(new Arc2D.Double(0.5, 2, 5, 5, 90, 180, Arc2D.OPEN));
        g.draw(new Arc2D.Double(10.5, 2, 5, 5, 270, 180, Arc2D.OPEN));
        g.fill(new Rectangle2D.Double(7, 9, 2, 3));
        g.fill(new Rectangle2D.Double(4.5, 12.5, 7, 2.5));
        break;
      case "check": // green tick in a filled circle
        g.setColor(new Color(76, 160, 76));
        g.fill(new Ellipse2D.Double(1, 1, 14, 14));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(4.5, 8.2, 7, 10.8));
        g.draw(new Line2D.Double(7, 10.8, 11.6, 5.4));
        break;
      case "chevron": // › pointing right
        g.setColor(new Color(150, 150, 150));
        g.draw(new Line2D.Double(6, 3.5, 10.5, 8));
        g.draw(new Line2D.Double(10.5, 8, 6, 12.5));
        break;
      case "play":
        g.setColor(Color.WHITE);
        g.fill(new Polygon(new int[] {4, 13, 4}, new int[] {2, 8, 14}, 3));
        break;
      case "pause":
        g.setColor(INK);
        g.fill(new Rectangle2D.Double(4, 3, 3, 10));
        g.fill(new Rectangle2D.Double(9.5, 3, 3, 10));
        break;
      case "flag":
        g.setColor(INK);
        g.draw(new Line2D.Double(3.5, 2, 3.5, 15));
        Path2D flag = new Path2D.Double();
        flag.moveTo(3.5, 2.5);
        flag.lineTo(13, 2.5);
        flag.lineTo(10.5, 5.5);
        flag.lineTo(13, 8.5);
        flag.lineTo(3.5, 8.5);
        g.draw(flag);
        break;
      case "trash":
        g.setColor(INK);
        g.draw(new Line2D.Double(2.5, 4, 13.5, 4));
        g.draw(new Line2D.Double(6, 4, 6.5, 2));
        g.draw(new Line2D.Double(6.5, 2, 9.5, 2));
        g.draw(new Line2D.Double(9.5, 2, 10, 4));
        g.draw(new RoundRectangle2D.Double(4, 4, 8, 10.5, 2, 2));
        g.draw(new Line2D.Double(6.7, 6.5, 6.7, 12));
        g.draw(new Line2D.Double(9.3, 6.5, 9.3, 12));
        break;
      case "box": // unticked tickbox: light fill so it shows on the dark cards
        g.setColor(new Color(150, 152, 155));
        g.fill(new RoundRectangle2D.Double(1.5, 1.5, 13, 13, 3, 3));
        g.setColor(new Color(205, 207, 210));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(1.5, 1.5, 13, 13, 3, 3));
        break;
      case "boxTicked": // ticked: gold with a dark tick
        g.setColor(GOLD);
        g.fill(new RoundRectangle2D.Double(1.5, 1.5, 13, 13, 3, 3));
        g.setColor(new Color(40, 30, 10));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(4.5, 8.2, 7, 10.8));
        g.draw(new Line2D.Double(7, 10.8, 11.6, 5.4));
        break;
      case "info": // ⓘ
        g.setColor(new Color(160, 160, 160));
        g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
        g.fill(new Ellipse2D.Double(7.1, 4, 1.8, 1.8));
        g.draw(new Line2D.Double(8, 7.5, 8, 11.5));
        break;
      default:
        break;
    }
    g.dispose();
    return new ImageIcon(img);
  }

  /** The same icon recoloured: every visible pixel takes {@code color}, keeping its opacity. */
  static ImageIcon tint(ImageIcon icon, Color color) {
    int w = icon.getIconWidth(), h = icon.getIconHeight();
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.drawImage(icon.getImage(), 0, 0, null);
    g.setComposite(AlphaComposite.SrcIn);
    g.setColor(color);
    g.fillRect(0, 0, w, h);
    g.dispose();
    return new ImageIcon(img);
  }
}
