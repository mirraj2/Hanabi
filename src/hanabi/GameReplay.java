package hanabi;

import jasonlib.Json;

import java.util.ArrayList;

import com.google.common.collect.Lists;

public class GameReplay {

  private ArrayList<TurnData> turns;
  private Json initialState;
  private int counter = 0;

  public GameReplay(Json initial) {
    this.initialState = initial;
    turns = Lists.newArrayList();
  }

  public void addTurn(byte[] data, String player) {
    turns.add(new TurnData(data, player));
  }

  public TurnData getNextTurn() {
    if (counter == turns.size()) {
      return null;
    }
    return turns.get(counter++);
  }

  public class TurnData {
    public byte[] data;
    public String player;

    public TurnData(byte[] data, String player) {
      this.data = data;
      this.player = player;
    }
  }

  public Json getStartingState() {
    return initialState;
  }

}
