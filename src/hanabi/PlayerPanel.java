package hanabi;

import jasonlib.IO;
import jasonlib.Json;
import jasonlib.Rect;
import jasonlib.swing.Graphics3D;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.google.common.collect.Lists;

public class PlayerPanel extends JComponent {

  private static final int gap = 10;
  private static final int leftGap = 32;
  private static final BufferedImage eye = IO.from(PlayerPanel.class, "eye.png").toImage();

  private final HanabiClient client;
  private final String player;
  private final List<Json> hand;
  private final boolean myHand;
  private Integer clickIndex;
  private int w;
  private boolean onEye = false;

  public PlayerPanel(HanabiClient client, String player, List<Json> hand, boolean myHand, boolean myTurn) {
    this.client = client;
    this.player = player;
    this.hand = hand;
    this.myHand = myHand;

    listen(myTurn);
  }

  @Override
  protected void paintComponent(Graphics gg) {
    Graphics3D g = Graphics3D.create(gg);

    w = (getWidth() - (leftGap + gap * hand.size())) / hand.size();
    int h = getHeight() - 2 * gap;

    int x = leftGap;
    int y = gap;
    Font font = new Font("Arial", Font.BOLD, 50);

    for (Json card : Lists.reverse(hand)) {
      int rank = card.getInt("rank");

      CardColor c = CardColor.valueOf(card.get("color"));

      Rect r = new Rect(x, y, w, h);
      
      boolean knownInfo = card.has("showRank") || card.has("showColor");

      boolean showInfo = !this.myHand;
      if (onEye) {
        showInfo = false;
      }

      if (knownInfo) {
        g.color(Color.red).fill(r);
        g.color(Color.black).fill(r.grow(-6, -6));
      } else {
        g.color(Color.black).fill(r);
      }
      if (showInfo || card.has("showColor")) {
        g.color(c.getColor()).fill(r.grow(-6, -6));
      }
      if (rank > 0) {
        g.font(font).color(Color.white).text(showInfo || card.has("showRank") ? rank + "" : "?", r);
      }

      x += gap;
      x += w;
    }

    if (!this.myHand && !player.equalsIgnoreCase("board")) {
      g.draw(eye, 0, getHeight() / 2 - eye.getHeight() / 2);
    }
  }

  private Action playAction = new AbstractAction("Play") {
    @Override
    public void actionPerformed(ActionEvent e) {
      client.play(clickIndex);
    }
  };

  private Action discardAction = new AbstractAction("Discard") {
    @Override
    public void actionPerformed(ActionEvent e) {
      client.discard(clickIndex);
    }
  };

  private Action colorHintAction = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      client.colorHint(player, clickIndex);
    }
  };

  private Action rankHintAction = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      client.rankHint(player, clickIndex);
    }
  };

  private void listen(boolean myTurn) {
    if (myHand) {
      if (myTurn) {
        addMouseListener(myHandListener);
      }
    } else {
      if (myTurn) {
        addMouseListener(otherHandListener);
      }
      addMouseMotionListener(otherHandListener);
    }
  }
  
  private Integer getClickIndex(int x, int y) {
    if (y < gap || y > getHeight() - gap) {
      return null;
    }
    x -= leftGap;
    y -= gap;

    int index = 0;
    while (x > w) {
      index++;
      x -= w + gap;
    }

    if (x < 0) {
      return null;
    }

    if (index >= hand.size()) {
      return null;
    }

    return hand.size() - index - 1; // we rendered the cards in reverse
  }

  private MouseAdapter myHandListener = new MouseAdapter() {
    @Override
    public void mousePressed(MouseEvent e) {
      clickIndex = getClickIndex(e.getX(), e.getY());
      if (clickIndex == null) {
        return;
      }

      JPopupMenu popup = new JPopupMenu();
      popup.add(playAction);
      popup.add(discardAction);
      popup.show(PlayerPanel.this, e.getX(), e.getY());
    };
  };

  private MouseAdapter otherHandListener = new MouseAdapter() {
    @Override
    public void mousePressed(MouseEvent e) {
      clickIndex = getClickIndex(e.getX(), e.getY());
      if (clickIndex == null) {
        return;
      }

      Json card = hand.get(clickIndex);

      JMenuItem colorHint = new JMenuItem(colorHintAction);
      JMenuItem rankHint = new JMenuItem(rankHintAction);
      colorHint.setText("Give " + card.get("color") + " Hint");
      rankHint.setText("Give " + card.getInt("rank") + "'s Hint");
      JPopupMenu popup = new JPopupMenu();
      popup.add(colorHint);
      popup.add(rankHint);
      popup.show(PlayerPanel.this, e.getX(), e.getY());
    };

    @Override
    public void mouseMoved(MouseEvent e) {
      onEye = e.getX() < leftGap && Math.abs(e.getY() - getHeight() / 2) <= 16;
      repaint();
    };

    @Override
    public void mouseExited(MouseEvent e) {
      onEye = false;
      repaint();
    };
  };

}
