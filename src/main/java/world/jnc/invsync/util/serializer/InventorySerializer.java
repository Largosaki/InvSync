package world.jnc.invsync.util.serializer;

import java.io.IOException;
import java.util.*;
import lombok.experimental.UtilityClass;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.DataView.SafetyMode;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.persistence.DataFormats;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import world.jnc.invsync.InventorySync;

@UtilityClass
public class InventorySerializer {
  static final DataQuery SLOT = DataQuery.of("slot");
  static final DataQuery STACK = DataQuery.of("stack");
  static final DataQuery ModItemInfo = DataQuery.of("UnsafeData", "moditeminfo");

  public static ItemStack CreateAlienBook(DataView data) {
    ItemStack book = ItemStack.builder().itemType(ItemTypes.WRITTEN_BOOK).build();
    String displayName = data.getName();
    book.offer(
        Keys.BOOK_AUTHOR, Text.of(TextColors.DARK_PURPLE, TextStyles.BOLD, "Ender Committee"));
    book.offer(
        Keys.DISPLAY_NAME, Text.of(TextColors.DARK_PURPLE, TextStyles.BOLD, displayName + "ember"));
    List<Text> pages = new ArrayList<>();
    pages.add(
        Text.of(
            "An item that has lost its power, but seems to retain the possibility of recovery"
                + "\nName:"
                + displayName
                + "\nCount:"
                + "\nUUID:"
                + java.util.UUID.randomUUID()));
    try {
      String json = GetJsonFormItem(data);
      book.tryOffer(Keys.BOOK_PAGES, pages);
      DataContainer container = book.toContainer();
      return ItemStack.builder().fromContainer(container.set(ModItemInfo, json)).build();
    } catch (IOException e) {
      e.printStackTrace();
      pages.add(Text.of("This item is broken:("));
      book.tryOffer(Keys.BOOK_PAGES, pages);
      return book;
    }
  }

  public static ItemStack RestoreAlienBook(ItemStack item) {
    try {
      ItemStack book;
      if (!IsOriginalBook(item)) {
        book = ItemStack.builder().itemType(ItemTypes.WRITTEN_BOOK).build();
        book.offer(
            Keys.BOOK_AUTHOR, Text.of(TextColors.DARK_PURPLE, TextStyles.BOLD, "Ender Committee"));
        book.offer(
            Keys.DISPLAY_NAME, Text.of(TextColors.DARK_PURPLE, TextStyles.BOLD, "Botched replica"));
        List<Text> pages = new ArrayList<>();
        pages.add(Text.of("catcat_king bitch"));
        book.tryOffer(Keys.BOOK_PAGES, pages);
      } else {
        String info = ReadItemStorageJson(item);
        book = ItemStack.builder().fromContainer(DataFormats.JSON.read(info)).build();
      }
      return book;
      // spawnItem(re, player.getLocation());
    } catch (Exception e) {
      InventorySync.getLogger().error(String.valueOf(e));
      return item;
    }
  }

  public static String GetJsonFormItem(DataView data) throws IOException {
    return DataFormats.JSON.write(data);
  }

  public static String ReadItemStorageJson(ItemStack item) {
    return item.toContainer().getString(ModItemInfo).get();
  }

  public static boolean IsAlienBook(ItemStack item) {
    return item.getType().equals(ItemTypes.WRITTEN_BOOK)
        && item.toContainer().contains(ModItemInfo);
  }

  public static boolean IsOriginalBook(ItemStack item) {
    return !item.toContainer().contains(DataQuery.of("UnsafeData", "generation"));
  }

  public static List<DataView> serializeInventory(Inventory inventory) {
    DataContainer container;
    List<DataView> slots = new LinkedList<>();

    int i = 0;
    Optional<ItemStack> stack;

    for (Inventory inv : inventory.slots()) {
      stack = inv.peek();

      if (stack.isPresent()) {
        container = DataContainer.createNew(SafetyMode.ALL_DATA_CLONED);

        container.set(SLOT, i);
        container.set(STACK, serializeItemStack(stack.get()));

        slots.add(container);
      }

      i++;
    }

    return slots;
  }

  public static boolean deserializeInventory(List<DataView> slots, Inventory inventory) {
    Map<Integer, ItemStack> stacks = new HashMap<>();
    int i;
    ItemStack stack;
    boolean fail = false;

    for (DataView slot : slots) {
      i = slot.getInt(SLOT).get();

      try {
        stack = deserializeItemStack(slot.getView(STACK).get());
        if (stack.isEmpty()) {
          stacks.put(i, stack);
          continue;
        }
        if (IsAlienBook(stack)) {
          ItemStack item = RestoreAlienBook(stack);
          stacks.put(i, item);
        } else {
          stacks.put(i, stack);
        }

      } catch (NoSuchElementException e) {
        ItemStack book = CreateAlienBook(slot.getView(STACK).get());
        stacks.put(i, book);
      }
    }

    i = 0;

    for (Inventory slot : inventory.slots()) {
      if (stacks.containsKey(i)) {
        try {
          slot.set(stacks.get(i));
        } catch (NoSuchElementException e) {
          slot.clear();

          fail = true;
        }
      } else {
        slot.clear();
      }

      ++i;
    }

    return fail;
  }

  static DataView serializeItemStack(ItemStack item) {
    return item.toContainer();
  }

  static ItemStack deserializeItemStack(DataView data) {
    return ItemStack.builder().fromContainer(data).build();
  }
}
