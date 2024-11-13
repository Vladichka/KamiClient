package auto;

import haven.*;
import haven.rx.Reactor;
import rx.functions.Action2;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static auto.InvHelper.*;


public class Bot implements Defer.Callable<Void> {
    private static final Object lock = new Object();
    private static Bot current;
    private final List<Target> targets;
    private final BotAction[] actions;
    private Defer.Future<Void> task;
    private boolean cancelled = false;
    private String message = null;
    private static final Object waiter = new Object();
    
    public Bot(List<Target> targets, BotAction... actions) {
	this.targets = targets;
	this.actions = actions;
    }
    
    public Bot(BotAction... actions) {
	this(Collections.singletonList(Target.EMPTY), actions);
    }
    
    @Override
    public Void call() throws InterruptedException {
	targets.forEach(Target::highlight);
	for (Target target : targets) {
	    for (BotAction action : actions) {
		if(target.disposed()) {break;}
		action.call(target, this);
		checkCancelled();
	    }
	}
	synchronized (lock) {
	    if(current == this) {current = null;}
	}
	return null;
    }
    
    private void run(Action2<Boolean, String> callback) {
	task = Defer.later(this);
	task.callback(() -> callback.call(task.cancelled(),  message));
    }
    
    private void checkCancelled() throws InterruptedException {
	if(cancelled) {
	    throw new InterruptedException();
	}
    }
    
    private void markCancelled() {
	cancelled = true;
	task.cancel();
    }
    
    public void cancel(String message) {
	this.message = message;
	markCancelled();
    }
    
    public void cancel() {
	cancel(null);
    }
    
    public static void cancelCurrent() {
	setCurrent(null);
    }
    private static void setCurrent(Bot bot) {
	synchronized (lock) {
	    if(current != null) {
		current.cancel();
	    }
	    current = bot;
	}
    }
    
    private static void start(Bot bot, UI ui) {
	start(bot, ui, false);
    }
    
    private static void start(Bot bot, UI ui, boolean silent) {
	setCurrent(bot);
	bot.run((error, message) -> {
	    if(!silent && CFG.SHOW_BOT_MESSAGES.get() || error) {
		GameUI.MsgType type = error ? GameUI.MsgType.ERROR : GameUI.MsgType.INFO;
		if(message == null) {
		    message = error
			? "Task is cancelled."
			: "Task is completed.";
		    type = GameUI.MsgType.INFO;
		}
		ui.message(message, type);
	    }
	});
    }
    
    public static void pickup(GameUI gui, String filter) {
	pickup(gui, filter, Integer.MAX_VALUE);
    }
    
    public static void pickup(GameUI gui, String filter, int limit) {
	pickup(gui, startsWith(filter), limit);
    }
    
    public static void pickup(GameUI gui, Predicate<Gob> filter) {
	pickup(gui, filter, Integer.MAX_VALUE);
    }
    
    public static void pickup(GameUI gui, Predicate<Gob> filter, int limit) {
	List<Target> targets = gui.ui.sess.glob.oc.stream()
	    .filter(filter)
	    .filter(gob -> distanceToPlayer(gob) <= CFG.AUTO_PICK_RADIUS.get())
	    .filter(Bot::isOnRadar)
	    .sorted(byDistance)
	    .limit(limit)
	    .map(Target::new)
	    .collect(Collectors.toList());
	
	start(new Bot(targets,
	    Target::rclick_shift,
	    (target, bot) -> target.gob.waitRemoval()
	), gui.ui);
    }
    
    public static void pickup(GameUI gui) {
	pickup(gui, has(GobTag.PICKUP));
    }
    
    public static void openGate(GameUI gui) {
	List<Target> targets = gui.ui.sess.glob.oc.stream()
	    .filter(has(GobTag.GATE))
	    .filter(gob -> !gob.isVisitorGate())
	    .filter(gob -> distanceToPlayer(gob) <= 35)
	    .sorted(byDistance)
	    .limit(1)
	    .map(Target::new)
	    .collect(Collectors.toList());
	
	start(new Bot(targets, Target::rclick), gui.ui, true);
    }
    
    public static void refillDrinks(GameUI gui) {
	if(gui.hand() != null || gui.cursor != null) {
	    gui.error("You must have empty cursor to refill drinks!");
	    return;
	}
	
	Coord2d waterTile = null;
	Gob barrel = null;
	boolean needWalk = false;
	Gob player = gui.map.player();
	BotAction interact;
	
	if(MapHelper.isPlayerOnFreshWaterTile(gui)) {
	    waterTile = player.rc;
	} else {
	    needWalk = true;
	    List<Target> objs = getNearestTargets(gui, GobTag.HAS_WATER, 1, 32);
	    if(!objs.isEmpty()) {
		barrel = objs.get(0).gob;
	    }
	    if(barrel == null) {
		waterTile = MapHelper.nearbyWaterTile(gui);
	    }
	}
	
	final Coord2d tile = barrel != null ? barrel.rc : waterTile;
	
	if(waterTile != null) {
	    interact = (t, b) -> gui.map.wdgmsg("itemact", Coord.z, tile.floor(OCache.posres), 0);
	} else if(barrel != null) {
	    final Gob gob = barrel;
	    interact = (t, b) -> gob.itemact(UI.MOD_META);
	    
	} else {
	    gui.error("You must be near tile or barrel with fresh water to refill drinks!");
	    return;
	}
	
	List<Target> targets = Stream.of(INVENTORY_CONTAINED(gui), BELT_CONTAINED(gui))
	    .flatMap(x -> x.get().stream())
	    .filter(InvHelper::isDrinkContainer)
	    .filter(InvHelper::isNotFull)
	    .map(Target::new)
	    .collect(Collectors.toList());
	
	if(targets.isEmpty()) {
	    gui.error("No non-full drink containers to refill!");
	    return;
	}
	
	Bot refillBot = new Bot(targets,
	    Target::take,
	    (t, b) -> waitHeldChanged(gui),
	    interact,
	    doWait(70),
	    Target::putBack,
	    (t, b) -> waitHeldChanged(gui)
	);
	if(needWalk) {
	    start(new Bot(
		(t, b) -> gui.map.click(tile, 1, Coord.z, tile.floor(OCache.posres), 1, 0),
		waitGobPose(player, 1500,"/walking", "/running"),
		waitGobNoPose(player, 1500,"/walking", "/running"),
		(t, b) -> start(refillBot, gui.ui, true)
	    ), gui.ui, true);
	} else {
	    start(refillBot, gui.ui, true);
	}
    }
    
    public static void selectFlower(GameUI gui, long gobid, String option) {
	List<Target> targets = gui.ui.sess.glob.oc.stream()
	    .filter(gob -> gob.id == gobid)
	    .map(Target::new)
	    .collect(Collectors.toList());
	
	selectFlower(gui, option, targets);
    }
    
    public static void selectFlowerOnItems(GameUI gui, String option, List<WItem> items) {
	List<Target> targets = items.stream()
	    .map(Target::new)
	    .collect(Collectors.toList());
    
	selectFlower(gui, option, targets);
    }
    
    public static void selectFlower(GameUI gui, String option, List<Target> targets) {
	start(new Bot(targets, Target::rclick, selectFlower(option)), gui.ui);
    }
    
    public static void drink(GameUI gui) {
	Collection<Supplier<List<WItem>>> everywhere = Arrays.asList(HANDS(gui), INVENTORY(gui), BELT(gui));
	Utils.chainOptionals(
	    () -> findFirstThatContains("Tea", everywhere),
	    () -> findFirstThatContains("Water", everywhere)
	).ifPresent(Bot::drink);
    }
    
    public static void drink(WItem item) {
	start(new Bot(Collections.singletonList(new Target(item)), Target::rclick, selectFlower("Drink")), item.ui, true);
    }
    
    public static void fuelGob(GameUI gui, String name, String fuel, int count) {
	List<Target> targets = getNearestTargets(gui, name, 1, 33);
	
	if(!targets.isEmpty()) {
	    start(new Bot(targets, fuelWith(gui, fuel, count)), gui.ui);
	} else {
	    gui.error("Cannot find target to add fuel to");
	}
    }
    
    private static List<Target> getNearestTargets(GameUI gui, String name, int limit, double distance) {
	return gui.ui.sess.glob.oc.stream()
	    .filter(gobIs(name))
	    .filter(gob -> distanceToPlayer(gob) <= distance)
	    .sorted(byDistance)
	    .limit(limit)
	    .map(Target::new)
	    .collect(Collectors.toList());
    }
    
    private static List<Target> getNearestTargets(GameUI gui, GobTag tag, int limit, double distance) {
	return gui.ui.sess.glob.oc.stream()
	    .filter(gobIs(tag))
	    .filter(gob -> distanceToPlayer(gob) <= distance)
	    .sorted(byDistance)
	    .limit(limit)
	    .map(Target::new)
	    .collect(Collectors.toList());
    }
    
    private static BotAction fuelWith(GameUI gui, String fuel, int count) {
	return (target, bot) -> {
	    Supplier<List<WItem>> inventory = unstacked(INVENTORY(gui));
	    float has = countItems(fuel, inventory);
	    if(has < count) {
		bot.cancel(String.format("Not enough '%s' in inventory: found %d, need: %d", fuel, (int) has, count));
		return;
	    }
	    for (int i = 0; i < count; i++) {
		Optional<WItem> w = findFirstItem(fuel, inventory);
		if(!w.isPresent()) {
		    bot.cancel("no fuel in inventory");
		    return;
		}
		w.get().take();
		if(!waitHeld(gui, fuel)) {
		    bot.cancel("no fuel on cursor");
		    return;
		}
		target.interact();
		if(!waitHeld(gui, null)) {
		    bot.cancel("cursor is not empty");
		    return;
		}
	    }
	};
    }
    
    private static BotAction waitGobNoPose(Gob gob, long timeout, String... poses) {
	return (t, b) -> {
	    final long started = System.currentTimeMillis();
	    while (System.currentTimeMillis() - started < timeout
		&& gob != null && !gob.disposed() && gob.hasPose(poses)) {
		pause(100);
	    }
	};
    }
    
    private static BotAction waitGobPose(Gob gob, long timeout, String... poses) {
	return (t, b) -> {
	    final long started = System.currentTimeMillis();
	    while (System.currentTimeMillis() - started < timeout
		&& gob != null && !gob.disposed() && !gob.hasPose(poses)) {
		pause(100);
	    }
	};
    }
    
    private static boolean isHeld(GameUI gui, String what) throws Loading {
	GameUI.DraggedItem drag = gui.hand();
	if(drag == null && what == null) {
	    return true;
	}
	if(drag != null && what != null) {
	    return drag.item.is2(what);
	}
	return false;
    }
    
    private static boolean waitHeld(GameUI gui, String what) {
	if(Boolean.TRUE.equals(doWaitLoad(() -> isHeld(gui, what)))) {
	    return true;
	}
	if(waitHeldChanged(gui)) {
	    return Boolean.TRUE.equals(doWaitLoad(() -> isHeld(gui, what)));
	}
	return false;
    }
    
    private static boolean waitHeldChanged(GameUI gui) {
	boolean result = true;
	try {
	    synchronized (gui.heldNotifier) {
		gui.heldNotifier.wait(5000);
	    }
	} catch (InterruptedException e) {
	    result = false;
	}
	return result;
    }
    
    private static <T> T doWaitLoad(Supplier<T> action) {
	T result = null;
	boolean ready = false;
	while (!ready) {
	    try {
		result = action.get();
		ready = true;
	    } catch (Loading e) {
		pause(100);
	    }
	}
	return result;
    }
    
    private static BotAction doWait(long ms) {
	return (t, b) -> pause(ms);
    }
    
    private static void pause(long ms) {
	synchronized (waiter) {
	    try {
		waiter.wait(ms);
	    } catch (InterruptedException ignore) {
	    }
	}
    }
    
    private static void unpause() {
	synchronized (waiter) { waiter.notifyAll(); }
    }
    
    private static Predicate<Gob> gobIs(String what) {
	return g -> {
	    if(g == null) { return false; }
	    String id = g.resid();
	    if(id == null) {return false;}
	    return id.contains(what);
	};
    }
    
    private static Predicate<Gob> gobIs(GobTag what) {
	return g -> {
	    if(g == null) { return false; }
	    return g.is(what);
	};
    }
    
    private static boolean isOnRadar(Gob gob) {
	if(!CFG.AUTO_PICK_ONLY_RADAR.get()) {return true;}
	Boolean onRadar = gob.isOnRadar();
	return onRadar == null || onRadar;
    }
    
    private static double distanceToPlayer(Gob gob) {
	Gob p = gob.glob.oc.getgob(gob.glob.sess.ui.gui.plid);
	return p.rc.dist(gob.rc);
    }
    
    public static Comparator<Gob> byDistance = (o1, o2) -> {
	try {
	    Gob p = o1.glob.oc.getgob(o1.glob.sess.ui.gui.plid);
	    return Double.compare(p.rc.dist(o1.rc), p.rc.dist(o2.rc));
	} catch (Exception ignored) {}
	return Long.compare(o1.id, o2.id);
    };
    
    private static BotAction selectFlower(String... options) {
	return (target, bot) -> {
	    if(target.hasMenu()) {
		FlowerMenu.lastTarget(target);
		Reactor.FLOWER.first().subscribe(flowerMenu -> {
		    Reactor.FLOWER_CHOICE.first().subscribe(choice -> unpause());
		    flowerMenu.forceChoose(options);
		});
		pause(5000);
	    }
	};
    }
    
    private static Predicate<Gob> startsWith(String text) {
	return gob -> {
	    try {
		return gob.getres().name.startsWith(text);
	    } catch (Exception ignored) {}
	    return false;
	};
    }
    
    private static Predicate<Gob> has(GobTag tag) {
	return gob -> gob.is(tag);
    }
    
    public interface BotAction {
	void call(Target target, Bot bot) throws InterruptedException;
    }
    
    //TODO: rework with inheritance?
    public static class Target {
	public static final Target EMPTY = new Target();
	
	public final Gob gob;
	public final WItem item;
	public final ContainedItem contained;
	
	private Target() {
	    this.gob = null;
	    this.item = null;
	    this.contained = null;
	}
	
	public Target(Gob gob) {
	    this.gob = gob;
	    this.item = null;
	    this.contained = null;
	}
	
	public Target(WItem item) {
	    this.item = item;
	    this.gob = null;
	    this.contained = null;
	}
	
	private Target(ContainedItem contained) {
	    this.item = null;
	    this.gob = null;
	    this.contained = contained;
	}
	
	public void rclick(Bot b) {rclick();}
	
	public void rclick() {
	    rclick(0);
	}
	
	public void rclick_shift(Bot b) {rclick_shift();}
	
	public void rclick_shift() {
	    rclick(UI.MOD_SHIFT);
	}
    
	public void rclick(int modflags) {
	    if(!disposed()) {
		if(gob != null) {gob.rclick(modflags);}
		if(item != null) {item.rclick(modflags);}
		if(contained != null) {contained.item.rclick(modflags);}
	    }
	}
    
	public void interact() {
	    if(!disposed()) {
		if(gob != null) {gob.itemact();}
		if(item != null) {/*TODO: implement*/}
	    }
	}
    
	public void highlight() {
	    if(!disposed()) {
		if(gob != null) {gob.highlight();}
	    }
	}
	
	public void take(Bot b) {take();}
	
	public void take() {
	    if(contained != null && !contained.itemDisposed()) {
		contained.take();
	    }
	}
	
	public void putBack(Bot b) {putBack();}
	
	public void putBack() {
	    if(contained != null && !contained.containerDisposed()) {
		contained.putBack();
	    }
	}
    
	public boolean hasMenu() {
	    if(gob != null) {return gob.is(GobTag.MENU);}
	    return item != null || contained != null;
	}
    
	public boolean disposed() {
	    return (item != null && item.disposed()) || (gob != null && gob.disposed());
	}
    }
}
