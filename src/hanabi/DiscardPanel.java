package hanabi;

import jasonlib.Json;
import jasonlib.Rect;
import jasonlib.swing.Graphics3D;
import jasonlib.swing.component.GPanel;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;

public class DiscardPanel extends GPanel {

  private final Multimap<CardColor, Integer> m = ArrayListMultimap.create();
  private boolean starmode = false;

  public void setStarmode(boolean starmode) {
    this.starmode = starmode;
  }

  @Override
  protected void paintComponent(Graphics gg) {
    Graphics3D g = Graphics3D.create(gg);

    g.color(Color.gray).draw(new Rect(0, 0, getWidth() - 1, getHeight() - 1));

    Font font = new Font("Arial", Font.BOLD, 14);
    g.font(font);

    int nColors = starmode ? 6 : 5;

    int gap = 5;
    int h = (getHeight() - gap * 4) / nColors;
    int x = 0;
    int y = 0;

    int w = 60;
    for (CardColor c : CardColor.values()) {
      if (!starmode && c.equals(CardColor.STAR)) {
        continue;
      }
      Collection<Integer> values = m.get(c);
      if (!values.isEmpty()) {
        w = Math.min(w, (getWidth() - gap * values.size()) / values.size());
      }
    }

    for (CardColor c : CardColor.values()) {
      if (!starmode && c.equals(CardColor.STAR)) {
        continue;
      }
      List<Integer> values = Ordering.natural().immutableSortedCopy(m.get(c));
      for (Integer value : values) {
        Rect r = new Rect(x, y, w, h);
        g.color(c.getColor()).fill(r).color(Color.white).text(value + "", r);
        g.color(Color.black).draw(r);
        x += w + gap;
      }
      y += h + gap;
      x = 0;
    }
  }

  public void update(Json discard) {
    m.clear();

    for (Json card : discard.asJsonArray()) {
      int rank = card.getInt("rank");
      CardColor c = CardColor.valueOf(card.get("color"));

      m.get(c).add(rank);
    }

    repaint();
  }

}
