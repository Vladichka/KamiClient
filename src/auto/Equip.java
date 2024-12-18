package auto;

import haven.*;
import haven.Equipory.SLOTS;

import java.util.Optional;

import static haven.Equipory.SLOTS.*;

public class Equip {
    public static final Item BOW = new Equip.Item("Bow", "/huntersbow", "/rangersbow");
    public static final Item SWORD = new Equip.Item("Sword", "/bronzesword", "/fyrdsword", "/hirdsword");
    public static final Item SHIELD = new Equip.Item("Shield", "/roundshield");
    public static final Item SPEAR = new Equip.Item("Spear", "/boarspear");

    //These items can't be placed into a belt
    private static final String[] FORBIDDEN = {
	"/bucket",
	"/pickingbasket",
	"/splint",
    };

    public static class Item {
	private final String name;
	private final String[] types;

	public Item(String name, String... types) {
	    this.name = name;
	    this.types = types;
	}
    }

    public static void twoHanded(GameUI gui, Item target) {
	Bot bot = Bot.execute((t, b) -> {
	    Equipory equipory = gui.equipory;

	    WItem leftHand = equipory.slot(HAND_LEFT);
	    String leftName = leftHand != null ? leftHand.item.resname() : null;

	    if(GobTag.ofType(leftName, target.types)) {
		b.cancel();
		return;
	    }

	    if(checkForbidden(gui, leftName, HAND_LEFT)) {
		b.cancel();
		return;
	    }

	    WItem rightHand = equipory.slot(HAND_RIGHT);
	    String rightName = rightHand != null ? rightHand.item.resname() : null;

	    if(checkForbidden(gui, rightName, HAND_RIGHT)) {
		b.cancel();
		return;
	    }

	    Optional<InvHelper.ContainedItem> opt = InvHelper.findFirstContained(InvHelper.ofType(target.types), InvHelper.BELT_CONTAINED(gui));
	    if(!opt.isPresent()) {
		b.cancel(target.name + " not found in belt.");
		return;
	    }

	    InvHelper.ContainedItem item = opt.get();

	    if(leftHand != null && rightHand != null && leftHand.item != rightHand.item) {
		if(!hasEmptySlot(equipory.slot(BELT))) {
		    b.cancel("You need an empty slot in your belt to swap to a " + target.name + ".");
		    return;
		}
		leftHand.take();
		BotUtil.waitHeldChanged(gui);
		item.putBack();
		BotUtil.waitHeldChanged(gui);
	    }

	    item.take();
	    equipory.sendDrop(HAND_LEFT);
	    BotUtil.waitHeldChanged(gui);

	    if(leftHand != null || rightHand != null) {
		item.putBack();
		BotUtil.waitHeldChanged(gui);
	    }
	});
	bot.start(gui.ui, true);
    }

    public static void twoItems(GameUI gui, Item first, Item second) {
	Bot bot = Bot.execute((t, b) -> {
	    Equipory equipory = gui.equipory;

	    WItem leftHand = equipory.slot(HAND_LEFT);
	    String leftName = leftHand != null ? leftHand.item.resname() : null;

	    if(checkForbidden(gui, leftName, HAND_LEFT)) {
		b.cancel();
		return;
	    }

	    WItem rightHand = equipory.slot(HAND_RIGHT);
	    String rightName = rightHand != null ? rightHand.item.resname() : null;

	    if(checkForbidden(gui, rightName, HAND_RIGHT)) {
		b.cancel();
		return;
	    }

	    SLOTS firstEquipped = GobTag.ofType(leftName, first.types) ? HAND_LEFT : GobTag.ofType(rightName, first.types) ? HAND_RIGHT : INVALID;
	    SLOTS secondEquipped = GobTag.ofType(leftName, second.types) ? HAND_LEFT : GobTag.ofType(rightName, second.types) ? HAND_RIGHT : INVALID;

	    if(firstEquipped != INVALID && secondEquipped != INVALID) {
		//both already equipped, nothing to do
		b.cancel();
		return;
	    }

	    Optional<InvHelper.ContainedItem> optFirst = InvHelper.findFirstContained(InvHelper.ofType(first.types), InvHelper.BELT_CONTAINED(gui));
	    if(firstEquipped == INVALID && !optFirst.isPresent()) {
		b.cancel(first.name + " not found in belt.");
		return;
	    }

	    Optional<InvHelper.ContainedItem> optSecond = InvHelper.findFirstContained(InvHelper.ofType(second.types), InvHelper.BELT_CONTAINED(gui));
	    if(secondEquipped == INVALID && !optSecond.isPresent()) {
		b.cancel(second.name + " not found in belt.");
		return;
	    }

	    SLOTS firstSlot = firstEquipped == INVALID
		? secondEquipped == HAND_LEFT ? HAND_RIGHT : HAND_LEFT
		: INVALID;

	    SLOTS secondSlot = secondEquipped == INVALID
		? firstEquipped == HAND_RIGHT ? HAND_LEFT : HAND_RIGHT
		: INVALID;

	    InvHelper.ContainedItem item;
	    if(firstSlot != INVALID) {
		item = optFirst.get();
		item.take();
		BotUtil.waitHeldChanged(gui);
		equipory.sendDrop(firstSlot);
		BotUtil.waitHeldChanged(gui);
		item.putBack();
		BotUtil.pause(5);
	    }

	    if(secondSlot != INVALID) {
		item = optSecond.get();
		item.take();
		BotUtil.waitHeldChanged(gui);
		equipory.sendDrop(secondSlot);
		BotUtil.waitHeldChanged(gui);
		item.putBack();
	    }
	});

	bot.start(gui.ui, true);
    }

    private static boolean hasEmptySlot(WItem belt) {
	return belt.item.contentswnd.children(Inventory.class).stream().anyMatch(i -> i.free() > 0);
    }

    private static boolean checkForbidden(GameUI gui, String name, SLOTS hand) {
	if(GobTag.ofType(name, FORBIDDEN)) {
	    gui.msg("Item in " + (hand == HAND_LEFT ? "left" : "right") + " hand can't be unequipped.", GameUI.MsgType.BAD);
	    return true;
	}
	return false;
    }
}
