package hanabi;

import static com.google.common.base.Preconditions.checkState;
import jasonlib.Json;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jexxus.common.Connection;
import jexxus.common.ConnectionListener;
import jexxus.server.Server;
import jexxus.server.ServerConnection;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class HanabiServer implements ConnectionListener {

  private static final Logger logger = Logger.getLogger(HanabiServer.class);

  private Map<Connection, String> connMap = Maps.newConcurrentMap();

  private Json watchers, players, board, discard, state, deck;

  public HanabiServer() {
    reset();
  }

  @Override
  public synchronized void receive(byte[] data, Connection from) {
    String name = connMap.get(from);

    Json json = new Json(data);

    String command = json.get("command");
    Json watchersJson = state.getJson("watchers");
    if (command.equals("login")) {
      String user = json.get("user");
      connMap.put(from, user);
      watchersJson.add(user);
      announce(user + " logged in.");
    } else if (command.equals("start_game")) {
      startGame();
    } else if (command.equals("play")) {
      play(json.getInt("index"), name);
    } else if (command.equals("discard")) {
      discard(json.getInt("index"), name);
    } else if (command.equals("colorHint")) {
      colorHint(json.get("target"), json.getInt("index"), name);
    } else if (command.equals("rankHint")) {
      rankHint(json.get("target"), json.getInt("index"), name);
    } else if (command.equals("reset")) {
      announce(name + " reset the server.");
      reset();
    } else if (command.equals("chat")) {
      announce(json.get("message"));
    } else {
      logger.error("Don't know command: " + json);
    }

    sendUpdate();
  }

  private void reset() {
    watchers = Json.array();
    players = Json.array();
    board = Json.array();
    discard = Json.array();
    state =
        Json.object().with("board", board).with("players", players).with("watchers", watchers)
            .with("discard", discard).with("gameOver", false);
    deck = Json.array();

    for (String player : connMap.values()) {
      watchers.add(player);
    }
  }

  private void colorHint(String target, int index, String from) {
    checkState(state.get("turn").equals(from));

    Json player = getPlayer(target);
    List<Json> hand = player.getJson("hand").asJsonArray();
    CardColor color = CardColor.valueOf(hand.get(index).get("color"));

    int count = 0;
    for (Json card : hand) {
      CardColor c = CardColor.valueOf(card.get("color"));
      if (c == color) {
        count++;
        card.with("showColor", true);
      }
    }

    state.with("cluesLeft", state.getInt("cluesLeft") - 1);

    announce(from + " told " + target + " about: " + count + " " + color + " card(s)");

    nextTurn();
  }

  private void rankHint(String target, int index, String from) {
    checkState(state.get("turn").equals(from));

    Json player = getPlayer(target);
    List<Json> hand = player.getJson("hand").asJsonArray();
    int rank = hand.get(index).getInt("rank");

    int count = 0;
    for (Json card : hand) {
      int r = card.getInt("rank");
      if (r == rank) {
        count++;
        card.with("showRank", true);
      }
    }

    state.with("cluesLeft", state.getInt("cluesLeft") - 1);

    announce(from + " told " + target + " about: " + count + " rank-" + rank + " card(s)");

    nextTurn();
  }

  private void play(int index, String from) {
    checkState(state.get("turn").equals(from));

    Json card = removeAndReplace(index, from);

    CardColor c = CardColor.valueOf(card.get("color"));
    Json boardSlot = board.asJsonArray().get(c.ordinal());
    int rank = boardSlot.getInt("rank");

    if (card.getInt("rank") == rank + 1) {
      // card successfully placed
      boardSlot.with("rank", rank + 1);
      announce(from + " successfully placed a " + c + " " + (rank + 1) + "!");
      if (rank + 1 == 5) {
        // they played a 5, so increase the number of hints by 1
        state.with("cluesLeft", state.getInt("cluesLeft") + 1);
      }
      if (checkForWin()) {
        announce("You win!");
        state.with("gameOver", true);
      }
    } else {
      // oh noo!
      state.with("mistakesLeft", state.getInt("mistakesLeft") - 1);
      announce(from + " tried to place a " + c + " " + card.getInt("rank") + " :(");
      if (state.getInt("mistakesLeft") <= 0) {
        announce("You've run out of bombs. You lose!");
        state.with("gameOver", true);
      }
      if (checkForLoss(card)) {
        announce("You can no longer complete the stacks. You lose!");
        state.with("gameOver", true);
      }
      discard.add(card);
    }
    nextTurn();
  }

  private boolean checkForWin() {
    for (Json boardSlot : board.asJsonArray()) {
      int rank = boardSlot.getInt("rank");
      if (rank != 5) {
        return false;
      }
    }
    return true;
  }

  private boolean checkForLoss(Json c) {
    int rank = c.getInt("rank");
    CardColor color = CardColor.valueOf(c.get("color"));
    if (rank == 5) {
      return true;
    }
    int match = 0;
    for (Json card : discard.asJsonArray()) {
      if (card.getInt("rank") == rank && CardColor.valueOf(card.get("color")).equals(color)) {
        match++;
      }
    }
    if (rank == 1) {
      return match == 2;
    } else {
      return match == 1;
    }
  }

  private void discard(int index, String from) {
    checkState(state.get("turn").equals(from));

    Json card = removeAndReplace(index, from);

    if (state.getInt("cluesLeft") < 8) {
      state.with("cluesLeft", state.getInt("cluesLeft") + 1);
    }

    announce(from + " discarded: " + card.get("color") + " " + card.getInt("rank"));
    if (checkForLoss(card)) {
      announce("You can no longer complete the stacks. You lose!");
      state.with("gameOver", true);
    }
    discard.add(card);

    nextTurn();
  }

  private Json removeAndReplace(int index, String player) {
    Json hand = getPlayer(player).getJson("hand");
    Json card = hand.asJsonArray().get(index);
    hand.remove(index);

    // add a new card into their hand
    if (!deck.isEmpty()) {
      hand.add(deck.asJsonArray().get(0));
      deck.remove(0);
    } else if (!state.getBoolean("lastRound")) {
      state.with("lastRound", true);
      state.with("endOn", player);
      announce("The deck is now empty. Each player gets one more turn.");
    }

    return card;
  }

  private void nextTurn() {
    int index = getCurrentPlayerIndex();
    index = (index + 1) % players.size();
    String player = players.asJsonArray().get(index).get("name");

    if (state.getBoolean("lastRound") && player.equals(state.get("endOn"))) {
        announce("Out of turns! Game over.");
        state.with("gameOver", true);
    }
    if (state.getBoolean("gameOver") != true) {
      state.with("turn", player);
    }
  }

  private int getCurrentPlayerIndex() {
    int c = -1;
    for (Json j : players.asJsonArray()) {
      c++;
      if (j.get("name").equals(state.get("turn"))) {
        return c;
      }
    }
    throw new IllegalStateException();
  }

  private Json getPlayer(String name) {
    for (Json j : players.asJsonArray()) {
      if (j.get("name").equals(name)) {
        return j;
      }
    }
    return null;
  }

  private void startGame() {
    checkState(players.isEmpty());
    // checkState(watchers.size() >= 2);
    checkState(watchers.size() <= 5);

    int handSize;
    if (players.size() <= 3) {
      handSize = 5;
    } else {
      handSize = 4;
    }

    state.with("cluesLeft", 8).with("mistakesLeft", 3);
    state.with("lastRound", false);

    List<Json> cards = Lists.newArrayList();
    for (CardColor c : CardColor.values()) {
      board.add(Json.object().with("color", c).with("rank", 0));
      for (int rank : new int[] {1, 1, 1, 2, 2, 3, 3, 4, 4, 5}) {
        cards.add(Json.object().with("color", c).with("rank", rank));
      }
    }
    Collections.shuffle(cards);

    for (String watcher : watchers) {
      List<Json> hand = cards.subList(0, handSize);
      Json player = Json.object().with("name", watcher).with("hand", Json.array(hand));
      players.add(player);
      hand.clear(); // remove the hand from the deck
    }
    watchers.clear();

    deck = Json.array(cards);
    state.with("deck", deck).with("turn", players.asJsonArray().get(0).get("name"));
  }

  @Override
  public synchronized void connectionBroken(Connection broken, boolean forced) {
    logger.info("Client disconnected: " + broken);
    String user = connMap.remove(broken);
    if (user != null) {
      state.getJson("watchers").remove(user);
      sendUpdate();
      announce(user + " has disconnected.");
    }
  }

  @Override
  public synchronized void clientConnected(ServerConnection conn) {
    logger.info("Client connected: " + conn);
  }

  private void announce(String s) {
    sendToAll(Json.object().with("command", "announce").with("text", s));
  }

  private void sendUpdate() {
    Json json = Json.object()
        .with("command", "state")
        .with("state", state);

    sendToAll(json);
  }

  private void sendToAll(Json json) {
    byte[] data = json.asByteArray();
    for (Connection conn : connMap.keySet()) {
      try {
        conn.send(data);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    BasicConfigurator.configure();
    Server server = new Server(new HanabiServer(), 19883, false);
    server.startServer();
    logger.debug("Server Started!");
  }



}
