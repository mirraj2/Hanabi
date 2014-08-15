package hanabi;

import java.awt.Color;

public enum CardColor {
  GRAY {
    @Override
    public Color getColor() {
      return new Color(100, 100, 100);
    }
  },
  ORANGE {
    @Override
    public Color getColor() {
      return new Color(255, 70, 0);
    }
  },
  PURPLE {
    @Override
    public Color getColor() {
      return new Color(87, 0, 127);
    }
  },
  GREEN {
    @Override
    public Color getColor() {
      return new Color(0, 150, 0);
    }
  },
  BLUE {
    @Override
    public Color getColor() {
      return Color.BLUE;
    }
  };

  public abstract Color getColor();

}
