/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import integrations.mapv4.MappingClient;
import me.ender.ui.CFGBox;
import me.ender.ui.CFGSlider;
import rx.functions.Action0;

import java.awt.*;
import java.util.*;
import java.awt.image.WritableRaster;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static haven.TileHighlight.*;

public class Inventory extends Widget implements DTarget {
    public static final Coord sqsz = UI.scale(new Coord(32, 32)).add(1, 1);
    public static final Tex invsq;
    public boolean dropul = true;
    private boolean canDropItems = false;
    private boolean dropEnabled = false;
    public ExtInventory ext;
    Action0 dropsCallback;
    public Coord isz;
    public int cachedSize = -1;
    public boolean[] sqmask = null;
    public static final Comparator<WItem> ITEM_COMPARATOR_ASC = new Comparator<WItem>() {
	@Override
	public int compare(WItem o1, WItem o2) {
	    QualityList ql1 = o1.itemq.get();
	    double q1 = (ql1 != null && !ql1.isEmpty()) ? ql1.single().value : 0;

	    QualityList ql2 = o2.itemq.get();
	    double q2 = (ql2 != null && !ql2.isEmpty()) ? ql2.single().value : 0;

	    return Double.compare(q1, q2);
	}
    };
    public static final Comparator<WItem> ITEM_COMPARATOR_DESC = new Comparator<WItem>() {
	@Override
	public int compare(WItem o1, WItem o2) {
	    return ITEM_COMPARATOR_ASC.compare(o2, o1);
	}
    };

    public boolean locked = false;
    Map<GItem, WItem> wmap = new HashMap<GItem, WItem>();

    static {
	Coord sz = sqsz.add(1, 1);
	WritableRaster buf = PUtils.imgraster(sz);
	for(int i = 1, y = sz.y - 1; i < sz.x - 1; i++) {
	    buf.setSample(i, 0, 0, 20); buf.setSample(i, 0, 1, 28); buf.setSample(i, 0, 2, 21); buf.setSample(i, 0, 3, 167);
	    buf.setSample(i, y, 0, 20); buf.setSample(i, y, 1, 28); buf.setSample(i, y, 2, 21); buf.setSample(i, y, 3, 167);
	}
	for(int i = 1, x = sz.x - 1; i < sz.y - 1; i++) {
	    buf.setSample(0, i, 0, 20); buf.setSample(0, i, 1, 28); buf.setSample(0, i, 2, 21); buf.setSample(0, i, 3, 167);
	    buf.setSample(x, i, 0, 20); buf.setSample(x, i, 1, 28); buf.setSample(x, i, 2, 21); buf.setSample(x, i, 3, 167);
	}
	for(int y = 1; y < sz.y - 1; y++) {
	    for(int x = 1; x < sz.x - 1; x++) {
		buf.setSample(x, y, 0, 36); buf.setSample(x, y, 1, 52); buf.setSample(x, y, 2, 38); buf.setSample(x, y, 3, 125);
	    }
	}
	invsq = new TexI(PUtils.rasterimg(buf));
    }

    @RName("inv")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new ExtInventory((Coord)args[0]));
	}
    }

    public void draw(GOut g) {
	Coord c = new Coord();
	int mo = 0;
	for(c.y = 0; c.y < isz.y; c.y++) {
	    for(c.x = 0; c.x < isz.x; c.x++) {
		if((sqmask != null) && sqmask[mo++]) {
		    g.chcolor(64, 64, 64, 255);
		    g.image(invsq, c.mul(sqsz));
		    g.chcolor();
		} else {
		    g.image(invsq, c.mul(sqsz));
		}
	    }
	}
	super.draw(g);
    }
	
    public Inventory(Coord sz) {
	super(sqsz.mul(sz).add(1, 1));
	isz = sz;
    }
    
    public boolean mousewheel(MouseWheelEvent ev) {
	if(locked){return false;}
	if(ui.modshift) {
	    ExtInventory minv = getparent(GameUI.class).maininvext;
	    if(minv != this.parent) {
		if(ev.a < 0)
		    wdgmsg("invxf", minv.wdgid(), 1);
		else if(ev.a > 0)
		    minv.wdgmsg("invxf", parent.wdgid(), 1);
	    }
	}
	return(true);
    }
    
    @Override
    public boolean mousedown(MouseDownEvent ev) {
	return locked || super.mousedown(ev);
    }

    public void addchild(Widget child, Object... args) {
	add(child);
	Coord c = (Coord)args[0];
	if(child instanceof GItem) {
	    GItem i = (GItem)child;
	    wmap.put(i, add(new WItem(i), c.mul(sqsz).add(1, 1)));
	    i.sendttupdate = canDropItems;
	    if(dropEnabled) {
		tryDrop(wmap.get(i));
	    }
	    itemsChanged();
	}
    }
    
    @Override
    public void destroy() {
	ItemAutoDrop.removeCallback(dropsCallback);
	super.destroy();
    }
    
    public void cdestroy(Widget w) {
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
	    ui.destroy(wmap.remove(i));
	    itemsChanged();
	}
    }
    
    public boolean drop(Coord cc, Coord ul) {
	if(!locked) {
	    Coord dc;
	    if(dropul)
		dc = ul.add(sqsz.div(2)).div(sqsz);
	    else
		dc = cc.div(sqsz);
	    wdgmsg("drop", dc);
	}
	return(true);
    }
	
    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }
	
    public void uimsg(String msg, Object... args) {
	if(msg.equals("sz")) {
	    isz = (Coord)args[0];
	    resize(invsq.sz().add(UI.scale(new Coord(-1, -1))).mul(isz).add(UI.scale(new Coord(1, 1))));
	    sqmask = null;
	    cachedSize = -1;
	} else if(msg == "mask") {
	    boolean[] nmask;
	    if(args[0] == null) {
		nmask = null;
	    } else {
		nmask = new boolean[isz.x * isz.y];
		byte[] raw = (byte[])args[0];
		for(int i = 0; i < isz.x * isz.y; i++)
		    nmask[i] = (raw[i >> 3] & (1 << (i & 7))) != 0;
	    }
	    this.sqmask = nmask;
	    cachedSize = -1;
	} else if(msg == "mode") {
	    dropul = !Utils.bv(args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(msg.equals("transfer-same")) {
	    process(getSame((GItem) args[0], (Boolean) args[1]), "transfer");
	} else if(msg.equals("drop-same")) {
	    process(getSame((GItem) args[0], (Boolean) args[1]), "drop");
	} else if(msg.equals("ttupdate") && sender instanceof GItem && wmap.containsKey(sender)) {
	    if(dropEnabled) {
		tryDrop(wmap.get(sender));
	    }
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    private void process(List<WItem> items, String action) {
	for (WItem item : items){
	    item.item.wdgmsg(action, Coord.z);
	}
    }

    private List<WItem> getSame(GItem item, Boolean ascending) {
	String name = item.resname();
	GSprite spr = item.spr();
	List<WItem> items = new ArrayList<>();
	for(Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
	    if(wdg.visible && wdg instanceof WItem) {
		WItem wItem = (WItem) wdg;
		GItem child = wItem.item;
		if(item.matches() == child.matches() && isSame(name, spr, child)) {
		    items.add(wItem);
		}
	    }
	}
	items.sort(ascending ? ITEM_COMPARATOR_ASC : ITEM_COMPARATOR_DESC);
	return items;
    }
    
    private static boolean isSame(String name, GSprite spr, GItem item) {
	try {
	    return item.resname().equals(name) && ((spr == item.spr()) || (spr != null && spr.same(item.spr())));
	} catch (Loading ignored) {}
	return false;
    }
    
    public int size() {
	if(cachedSize >= 0) {return cachedSize;}
	
	if(sqmask == null) {
	    cachedSize = isz.x * isz.y;
	} else {
	    cachedSize = 0;
	    for (boolean b : sqmask) {
		if(!b) {cachedSize++;}
	    }
	}
	
	return cachedSize;
    }
    
    public int filled() {
	int count = 0;
	for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
	    if(wdg instanceof WItem) {
		Coord sz = ((WItem) wdg).lsz;
		count += sz.x * sz.y;
	    }
	}
	return count;
    }
    
    public int free() {
	return size() - filled();
    }
    
    public Coord findPlaceFor(Coord size) {
	boolean[] slots = new boolean[isz.x * isz.y];
	for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
	    if(wdg instanceof WItem) {
		Coord p = wdg.c.div(sqsz);
		Coord sz = ((WItem) wdg).lsz;
		fill(slots, isz, p, sz);
	    }
	}
	Coord t = new Coord(0, 0);
	for (t.y = 0; t.y <= isz.y - size.y; t.y++) {
	    for (t.x = 0; t.x <= isz.x - size.x; t.x++) {
		if(fits(slots, isz, t, size)) {
		    return t;
		}
	    }
	}
	return null;
    }
    
    private static void fill(boolean[] slots, Coord isz, Coord p, Coord sz) {
	for (int x = 0; x < sz.x; x++) {
	    for (int y = 0; y < sz.y; y++) {
		if(p.x + x < isz.x && p.y + y < isz.y) {
		    slots[p.x + x + isz.x * (p.y + y)] = true;
		}
	    }
	}
    }
    
    private static boolean fits(boolean[] slots, Coord isz, Coord p, Coord sz) {
	for (int x = 0; x < sz.x; x++) {
	    if(p.x + x >= isz.x) {return false;}
	    for (int y = 0; y < sz.y; y++) {
		if(p.y + y >= isz.y) {return false;}
		if(slots[p.x + x + isz.x * (p.y + y)]) return false;
	    }
	}
	return true;
    }
    
    public void enableDrops() {
        Window wnd = getparent(Window.class);
	if(wnd != null) {
	    canDropItems = true;
	    dropsCallback = this::doDrops;
	    ItemAutoDrop.addCallback(dropsCallback);
	    wnd.addtwdg(new ICheckBox("gfx/hud/btn-adrop", "", "-d", "-h")
		.changed(this::doEnableDrops)
		.rclick(this::showDropCFG)
		.settip("Left-click to toggle item dropping\nRight-click to open settings", true)
	    );
	}
    }
    
    public ICheckBox sortBox;
    
    public void enableSort() {
	Window wnd = getparent(Window.class);
	if(wnd != null) {
	    Widget box = new ICheckBox("gfx/hud/btn-sort", "", "-d", "-h")
		.changed(this::showSortWindow)
		.rclick(this::doSortStd)
		.settip("Left-click to open sorting dialog\nRight-click to standard sort", true);
	    wnd.addtwdg(box);
	    sortBox = (ICheckBox)box;
	}
    }
    
    public void itemsChanged() {
	if(ext != null) {ext.itemsChanged();}
	GItem.ContentsWindow cnt = getparent(GItem.ContentsWindow.class);
	if(cnt != null) {
	    Inventory inv = cnt.cont.getparent(Inventory.class);
	    if(inv != null) {
		inv.itemsChanged();
	    }
	}
    }
    
    private void showDropCFG() {
	ItemAutoDrop.toggle(ui);
    }
    
    private void doEnableDrops(boolean v) {
	dropEnabled = v;
	doDrops();
    }
    
    public void doDrops() {
	if(dropEnabled) {
	    for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
		if(wdg instanceof WItem) {
		    tryDrop(((WItem) wdg));
		}
	    }
	}
    }
    
    private void tryDrop(WItem w) {
	if(w != null) { w.tryDrop(); }
    }
    
    public static Coord invsz(Coord sz) {
	return invsq.sz().add(new Coord(-1, -1)).mul(sz).add(new Coord(1, 1));
    }

    public static Coord sqroff(Coord c){
	return c.div(invsq.sz());
    }

    public static Coord sqoff(Coord c){
	return c.mul(invsq.sz());
    }

    public void forEachItem(BiConsumer<GItem, WItem> consumer) {
	wmap.forEach(consumer);
    }
    
    // KamiClient: Sorting stuff from ArdClient
    
    public void sort(String s) {
	//PBotUtils.sysMsg(ui, "Sorting! Please don't move!");
	List<InvItem> items = new ArrayList<>();
	List<Integer> ignoreSlots = new ArrayList<>();
	Coord c1 = Coord.of(1);
	for (Widget wdg = child; wdg != null; wdg = wdg.next) {
	    if (wdg instanceof WItem) {
		InvItem item = new InvItem((WItem) wdg);
		Coord sz = item.getSize();
		if (!sz.equals(c1)) {
		    Coord slot = item.getSlot();
		    for (int y = 0; y < sz.y; y++) {
			for (int x = 0; x < sz.x; x++) {
			    ignoreSlots.add(coordToSloti(slot.add(x, y)));
			}
		    }
//                    PBotUtils.sysMsg(ui, "Not support large items! " + item.getName());
//                    return;
		    continue;
		}
		items.add(item);
	    }
	}
	
	items.sort(Comparator.comparing(InvItem::getSloti));
	if (s.contains("!n"))
	    items.sort((o1, o2) -> o2.getName().compareTo(o1.getName()));
	else if (s.contains("n"))
	    items.sort(Comparator.comparing(InvItem::getName));
	if (s.contains("!q"))
	    items.sort((o1, o2) -> {
		if (o1.getQuality() == null && o2.getQuality() == null) return (0);
		if (o1.getQuality() == null && o2.getQuality() != null) return (-1);
		if (o1.getQuality() != null && o2.getQuality() == null) return (1);
		return Objects.requireNonNull(o2.getQuality()).compareTo(o1.getQuality());
	    });
	else if (s.contains("q"))
	    items.sort((o1, o2) -> {
		if (o1.getQuality() == null && o2.getQuality() == null) return (0);
		if (o1.getQuality() == null && o2.getQuality() != null) return (1);
		if (o1.getQuality() != null && o2.getQuality() == null) return (-1);
		return Objects.requireNonNull(o1.getQuality()).compareTo(o2.getQuality());
	    });
	
	if (s.contains("!r"))
	    items.sort((o1, o2) -> o2.getResname().compareTo(o1.getResname()));
	else if (s.contains("r"))
	    items.sort(Comparator.comparing(InvItem::getResname));
	
	sort:
	for (int i = 0; i < items.size(); i++) {
	    InvItem invItem = items.get(i);
	    InvItem iItem = getItem(invItem.getSlot());
	    AtomicInteger targetSloti = new AtomicInteger(i);
	    ignoreSlots.stream().filter(sl -> sl <= targetSloti.get()).forEach(sl -> targetSloti.getAndIncrement());
	    if (invItem.equals(iItem) && invItem.getSloti() != targetSloti.get()) {
		if (invItem.take() == null)
		    break;
		InvItem item = getItem(slotiToCoord(targetSloti.get()));
		if (!drop(slotiToCoord(targetSloti.get())))
		    break;
		while (item != null && ui.gui.vhand != null) {
		    Integer in = getInt(items, item, ignoreSlots);
		    if (in == null)
			break;
		    item = getItem(slotiToCoord(in));
		    if (!drop(slotiToCoord(in)))
			break sort;
		}
	    }
	}
	//PBotUtils.sysMsg(ui, "Sorting finished!");
    }
    
    public class InvItem {
	private final WItem wItem;
	private GItem gItem;
	private String resname;
	private String name;
	private boolean qinit = false;
	private Double quality;
	private Coord slot;
	private Integer sloti;
	private Coord size;
	
	public InvItem(WItem wItem) {
	    this.wItem = wItem;
	}
	
	public WItem getWItem() {
	    return (this.wItem);
	}
	
	public GItem getGItem() {
	    if (this.gItem == null)
		this.gItem = getWItem().item;
	    return (this.gItem);
	}
	
	public String getResname() {
	    if (this.resname == null) {
		Resource res = null;
		for (int sleep = 10; res == null; ) {
		    res = getGItem().resource();
		    sleep(sleep);
		}
		this.resname = res.name;
	    }
	    return (this.resname);
	}
	
	public String getName() {
	    if (this.name == null) {
		this.name = gItem.name.get();
	    }
	    return (this.name);
	}
	
	public Double getQuality() {
	    if (!this.qinit) {
		QualityList ql1 = wItem.itemq.get();
		this.quality = (ql1 != null && !ql1.isEmpty()) ? ql1.single().value : 0;
		this.qinit = true;
	    }
	    return (this.quality);
	}
	
	public Coord getSlot() {
	    if (this.slot == null) {
		this.slot = getWItem().c.sub(1, 1).div(UI.scale(sqsz.x, sqsz.y));
	    }
	    return (this.slot);
	}
	
	public Integer getSloti() {
	    if (this.sloti == null) {
		this.sloti = coordToSloti(getSlot());
	    }
	    return (this.sloti);
	}
	
	public Coord getSize() {
	    if (this.size == null) {
		GSprite spr = null;
		for (int sleep = 10; spr == null; ) {
		    spr = getGItem().spr();
		    sleep(sleep);
		}
		this.size = spr.sz().div(UI.scale(30));
	    }
	    return (this.size);
	}
	
	public WItem take() {
	    for (int i = 0; i < 5; i++) {
		getGItem().wdgmsg("take", Coord.z);
		
		for (int t = 0, sleep = 10; ui.gui.vhand == null; t += sleep) {
		    if (t >= 1000)
			break;
		    else
			sleep(sleep);
		}
		if (ui.gui.vhand != null)
		    break;
	    }
	    return (ui.gui.vhand);
	}
	
	@Override
	public String toString() {
	    return "InvItem{" +
		"wItem=" + wItem +
		", resname='" + resname + '\'' +
		", name='" + name + '\'' +
		", quality=" + quality +
		", slot=" + slot +
		", sloti=" + sloti +
		", slotrollback=" + slotiToCoord(sloti) +
		", size=" + size +
		'}';
	}
	
	@Override
	public boolean equals(Object o) {
	    if (this == o) return true;
	    if (!(o instanceof InvItem)) return false;
	    InvItem invItem = (InvItem) o;
	    return getWItem().equals(invItem.getWItem());
	}
	
	@Override
	public int hashCode() {
	    return Objects.hash(wItem);
	}
    }
    
    public Coord slotiToCoord(int slot) {
	Coord c = new Coord();
	int mo = 0;
	int max = 0;
	for (c.y = 0; c.y < isz.y; c.y++) {
	    for (c.x = 0; c.x < isz.x; c.x++) {
		if (sqmask == null || !sqmask[mo++]) {
		    if (slot == max) return (c);
		    else max++;
		}
	    }
	}
	return (null);
    }
    
    public Integer coordToSloti(Coord slot) {
	Coord c = new Coord();
	int mo = 0;
	int max = 0;
	for (c.y = 0; c.y < isz.y; c.y++) {
	    for (c.x = 0; c.x < isz.x; c.x++) {
		if (sqmask == null || !sqmask[mo++]) {
		    if (slot.x == c.x && slot.y == c.y) return (max);
		    else max++;
		}
	    }
	}
	return (null);
    }
    
    public boolean drop(Coord slot) {
	InvItem item = getItem(slot);
	wdgmsg("drop", slot);
	InvItem nitem = getItem(slot);
	for (int sleep = 10; nitem == null || nitem == item; ) {
	    nitem = getItem(slot);
	    sleep(sleep);
	}
	return (true);
    }
    
    public InvItem getItem(Coord slot) {
	InvItem item = null;
	for (Widget wdg = child; wdg != null; wdg = wdg.next) {
	    if (wdg instanceof WItem) {
		WItem w = (WItem) wdg;
		if (w.c.sub(1, 1).div(UI.scale(sqsz.x, sqsz.y)).equals(slot)) {
		    item = new InvItem(w);
		    break;
		}
	    }
	}
	return (item);
    }
    
    public Integer getInt(List<InvItem> items, InvItem item, List<Integer> ignoreSlots) {
	for (int i = 0; i < items.size(); i++)
	    if (items.get(i).equals(item)) {
		AtomicInteger targetSloti = new AtomicInteger(i);
		ignoreSlots.stream().filter(sl -> sl <= targetSloti.get()).forEach(sl -> targetSloti.getAndIncrement());
		return (targetSloti.get());
	    }
	return (null);
    }
    
    public static void sleep(int t) {
	try {
	    Thread.sleep(t);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }
    
    public void doSortStd() {
	String s = "";
	if (CFG.QUALITY_SORT.get() == 2)
	    s = s.concat("q");
	else if (CFG.QUALITY_SORT.get() == 0)
	    s = s.concat("!q");
	if (CFG.RESNAME_SORT.get() == 2)
	    s = s.concat("r");
	else if (CFG.RESNAME_SORT.get() == 0)
	    s = s.concat("!r");
	if (CFG.NAME_SORT.get() == 2)
	    s = s.concat("n");
	else if (CFG.NAME_SORT.get() == 0)
	    s = s.concat("!n");
	doSort(s);
    }
    
    public void doSort(String s) {
	Defer.later(() -> {
	    try {
		sort(s);
	    } catch (Exception e) {
	    }
	    return (null);
	});
    }
    
    public CFGWnd wnd;
    
    public void showSortWindow(boolean v)
    {
	if(wnd == null) {
	    wnd = ui.gui.add(new CFGWnd(this), ui.gui.invwnd.c);
	} else {
	    wnd.destroy();
	}
    }
    
    public static class CFGWnd extends WindowX {
	public final Inventory inv;
	
	public CFGWnd(Inventory inv) {
	    super(Coord.z, "Sort Settings");
	    
	    this.inv = inv;
	    
	    int STEP = UI.scale(25);
	    int START = 0;
	    int x, y;
	    
	    x = 0;
	    y = START;
	    
	    y = addSlider(CFG.QUALITY_SORT, 0, 2, "Quality sort: %d", "0 = reversed; 1 = none; 2 = normal", x, y, STEP);
	    
	    y += STEP;
	    y = addSlider(CFG.NAME_SORT, 0, 2, "Name sort: %d", "0 = reversed; 1 = none; 2 = normal", x, y, STEP);
	    
	    y += STEP;
	    y = addSlider(CFG.RESNAME_SORT, 0, 2, "Resname sort: %d", "0 = reversed; 1 = none; 2 = normal", x, y, STEP);
	    
	    y += STEP;
	    add(new Button(UI.scale(100), "Sort", false) {
		@Override
		public void click() {
		    try {
			String s = "";
			if (CFG.QUALITY_SORT.get() == 2)
			    s = s.concat("q");
			else if (CFG.QUALITY_SORT.get() == 0)
			    s = s.concat("!q");
			if (CFG.RESNAME_SORT.get() == 2)
			    s = s.concat("r");
			else if (CFG.RESNAME_SORT.get() == 0)
			    s = s.concat("!r");
			if (CFG.NAME_SORT.get() == 2)
			    s = s.concat("n");
			else if (CFG.NAME_SORT.get() == 0)
			    s = s.concat("!n");
			inv.doSort(s);
			closeThis();
		    } catch (Exception ex) {}
		    
		}
	    }, x, y);
	    
	    pack();

	}
	
	@Override
	public void destroy() {
	    inv.wnd = null;
	    super.destroy();
	}
	
	public void closeThis()
	{
	    inv.sortBox.set(false);
	}
	
	public void wdgmsg(Widget sender, String msg, Object... args) {
	    if((sender == this) && (msg == "close")) {
		closeThis();
	    } else {
		super.wdgmsg(sender, msg, args);
	    }
	}
	
	private int addSlider(CFG<Integer> cfg, int min, int max, String format, String tip, int x, int y, int STEP) {
	    final Label label = add(new Label(""), x, y);
	    label.settip(tip);
	    
	    y += STEP;
	    add(new CFGSlider(UI.scale(200), min, max, cfg, label, format), x, y).settip(tip);
	    
	    return y;
	}

    }
}
