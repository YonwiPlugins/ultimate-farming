package com.yonwi.farming;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/** Item pictures for the sidebar, loaded on the client thread and cached (Swing thread only). */
final class ItemPictures {
  private final ItemManager items;
  private final ClientThread clientThread;
  /** Everything the account owns, to show your own variant of an item; null when unknown. */
  private final Supplier<Set<Integer>> owned;
  private final BooleanSupplier disposed;
  /** Item pictures already loaded, by "id|width|height" or "id|full". */
  private final Map<String, Icon> cache = new HashMap<>();
  /** Item names by ID, for a variant's tooltip ("Infernal axe" rather than "Axe"). */
  private final Map<Integer, String> names = new HashMap<>();

  ItemPictures(ItemManager items, ClientThread clientThread, Supplier<Set<Integer>> owned, BooleanSupplier disposed) {
    this.items = items;
    this.clientThread = clientThread;
    this.owned = owned;
    this.disposed = disposed;
  }

  /** The picture to show for a kit item: your best variant of it (e.g. your infernal axe). */
  int shownId(String name) {
    return FarmingData.bestOwned(name, owned.get());
  }

  /** The item's full-size picture, with its name (or your variant's name) as the tooltip. */
  void full(JLabel target, String name) {
    int id = shownId(name);
    if (id == 0) {
      target.setText("•");
      target.setToolTipText(name);
      return;
    }
    target.setToolTipText(name);
    // A specific variant (your axe, your whistle): name it.
    if (id != FarmingData.id(name)) nameVariant(target, id);
    String key = id + "|full";
    Icon cached = cache.get(key);
    if (cached != null) {
      target.setIcon(cached); // no flicker on redraw
      return;
    }
    clientThread.invoke(
        () -> {
          if (disposed.getAsBoolean()) return;
          AsyncBufferedImage image = items.getImage(id);
          Runnable apply =
              () ->
                  SwingUtilities.invokeLater(
                      () -> {
                        ImageIcon icon = new ImageIcon(image);
                        target.setIcon(icon);
                        cache.put(key, icon);
                      });
          image.onLoaded(apply);
          apply.run();
        });
  }

  private void nameVariant(JLabel target, int id) {
    String known = names.get(id);
    if (known != null) {
      target.setToolTipText(known);
      return;
    }
    clientThread.invoke(
        () -> {
          String real = items.getItemComposition(id).getName();
          SwingUtilities.invokeLater(
              () -> {
                names.put(id, real);
                target.setToolTipText(real);
              });
        });
  }

  /** An item image scaled to fit, so it isn't cropped in small spaces. */
  void small(JLabel target, String name, int width, int height) {
    int id = shownId(name);
    if (id == 0) return;
    target.setToolTipText(name);
    // Already loaded once: set it now, so redrawing the tab doesn't make the pictures flicker.
    String key = id + "|" + width + "|" + height;
    Icon cached = cache.get(key);
    if (cached != null) {
      target.setIcon(cached);
      return;
    }
    clientThread.invoke(
        () -> {
          if (disposed.getAsBoolean()) return;
          AsyncBufferedImage image = items.getImage(id);
          Runnable apply =
              () ->
                  SwingUtilities.invokeLater(
                      () -> {
                        ImageIcon icon = new ImageIcon(ImageUtil.resizeImage(image, width, height, true));
                        target.setIcon(icon);
                        cache.put(key, icon);
                      });
          image.onLoaded(apply);
          apply.run();
        });
  }
}
