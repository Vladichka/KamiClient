package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import haven.MenuGrid.Pagina;
import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.level.Level;
import haven.res.ui.tt.slot.Slotted;
import haven.res.ui.tt.slots.ISlots;
import haven.res.ui.tt.wear.Wear;
import haven.resutil.Curiosity;
import haven.resutil.FoodInfo;
import me.ender.Reflect;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static haven.BAttrWnd.Constipations.*;
import static haven.QualityList.SingleType.*;

public class ItemData {
    public static boolean DBG = Config.get().getprop("ender.debug.items", "off").equals("on");
    public static final String WATER = "Water";
    public static final String TEA = "Tea";
    
    private static final ItemData EMPTY = new ItemData();
    private static Gson gson;
    private static final Map<String, ItemData> item_data = new LinkedHashMap<String, ItemData>(9, 0.75f, true) {
	private static final long serialVersionUID = 1L;

	protected boolean removeEldestEntry(Map.Entry<String, ItemData> eldest) {
	    return size() > 75;
	}

    };
    private Curiosity.Data curiosity;
    private FoodInfo.Data food;
    private Integer wear;
    private ArmorData armor;
    private GastronomyData gast;
    private Map<Resource, Integer> attributes;
    private SlotsData slots;
    private SlottedData gilding;
    
    
    private ItemData(GItem item) {
	this(item.info());
    }

    private ItemData(List<ItemInfo> info) {
	init(info);
    }

    private ItemData() {}

    public void init(List<ItemInfo> info) {
	for (ItemInfo ii : info) {
	    String className = ii.getClass().getCanonicalName();
	    QualityList q = QualityList.make(ItemInfo.findall(QualityList.classname, info));

	    if(ii instanceof Curiosity) {
		curiosity = new Curiosity.Data((Curiosity) ii, q);
	    } else if(ii instanceof FoodInfo) {
		food = new FoodInfo.Data((FoodInfo) ii, q);
	    } else if("Gast".equals(className)) {
		gast = new GastronomyData(ii, q);
	    } else if(ii instanceof ISlots) {
		slots = SlotsData.make((ISlots) ii);
	    } else if(ii instanceof Slotted) {
		gilding = SlottedData.make((Slotted) ii, q);
	    }
	    
	    Pair<Integer, Integer> a = ItemInfo.getArmor(info);
	    if(a != null) {
		armor = new ArmorData(a, q);
	    }
	    
	    Wear w = ItemInfo.getWear(info);
	    if(w != null) {
		QualityList.Quality single = q.single(Quality);
		if(single == null) {
		    single = QualityList.DEFAULT;
		}
		wear = (int) Math.round(w.m / (a != null ? single.value / 10.0 : single.multiplier));
	    }
	    
	    List<AttrMod> attrs = ItemInfo.findall(AttrMod.class, info);
	    if(!attrs.isEmpty()){
		attributes = AttrData.parse(attrs, q);
	    }
	}
    }

    public static Tex longtip(Pagina pagina, Session sess, boolean widePagina) {
        return longtip(pagina, sess, widePagina, 0, 0);
    }
    
    public static Tex longtip(Pagina pagina, Session sess, boolean widePagina, int titleSize, int titleSpace) {
	List<ItemInfo> infos = pagina.button().info();
	if(infos == null || infos.isEmpty()) {
	    return ItemData.get(pagina).longtip(pagina.res(), sess, widePagina, titleSize, titleSpace);
	}
	return longtip(pagina.res(), infos, widePagina, titleSize, titleSpace);
    }

    private static Tex longtip(Resource res, List<ItemInfo> infos, boolean widePagina, int titleSize, int titleSpace) {
	Resource.AButton ad = res.layer(Resource.action);
	Resource.Pagina pg = res.layer(Resource.pagina);
	Resource.Tooltip tip = res.layer(Resource.tooltip);
	String spacing = new String(new char[titleSpace]).replace("\0", " ");
	String tt = String.format("$b{%s%s}", spacing, ad != null ? ad.name : (tip != null) ? tip.t : res.name);
	if(titleSize > 0) {
	    tt = String.format("$size[%d]{%s}", titleSize, tt);
	}
	if(pg == null) {widePagina = false;}
	
	if(widePagina) {
	    tt += "\n\n" + pg.text;
	    infos = infos.stream()
		.filter(i -> !(i instanceof ItemInfo.Pagina))
		.collect(Collectors.toList());
	}

	BufferedImage img = MenuGrid.ttfnd.render(tt, UI.scale(300)).img;

	if(!infos.isEmpty()) {
	    img = ItemInfo.catimgs(UI.scale(20), img, ItemInfo.longtip(infos));
	}
	return new TexI(img);
    }

    private Tex longtip(Resource res, Session sess, boolean widePagina, int titleSize, int titleSpace) {
	return longtip(res, iteminfo(sess), widePagina, titleSize, titleSpace);
    }
    
    public List<ItemInfo> iteminfo(Session sess) {
	ITipData[] data = new ITipData[]{
	    curiosity,
	    food,
	    WearData.make(wear),
	    armor,
	    gast,
	    AttrData.make(attributes),
	    slots,
	    gilding
	    
	};
	List<ItemInfo> infos = new ArrayList<>(data.length);
	for (ITipData tip : data) {
	    if(tip != null) {
		infos.add(tip.create(sess));
	    }
	}
	return infos;
    }
    
    public static ItemData get(String name) {
	if(item_data.containsKey(name)) {
	    return item_data.get(name);
	}
	ItemData data = load(name);
	if(data == null) {data = EMPTY;}
	return data;
    }

    public static ItemData get(Pagina p){
	List<ItemInfo> infos = p.button().info();
	if(infos == null || infos.isEmpty()){
	    return ItemData.get(p.res().name);
	}
        return new ItemData(infos);
    }

    public static void actualize(GItem item, Pagina pagina) {
	if(item.resname() == null) { return; }

	ItemData data = new ItemData(item);
	String name = pagina.res().name;
	item_data.put(name, data);
	store(name, data);
    }

    private static ItemData load(String name) {
	ItemData data = parse(Config.loadFile(getFilename(name)));
	if(data != null) {
	    item_data.put(name, data);
	}
	return data;
    }

    private static void store(String name, ItemData data) {
        Config.saveFile(getFilename(name), getGson().toJson(data));
    }

    private static String getFilename(String name) {
	return "/item_data/" + name + ".json";
    }

    private static ItemData parse(String json) {
	ItemData data = null;
	try {
	    data = getGson().fromJson(json, ItemData.class);
	} catch (JsonSyntaxException ignore) {
	}
	return data;
    }

    private static Gson getGson() {
	if(gson == null) {
	    GsonBuilder builder = new GsonBuilder();
	    builder.setPrettyPrinting();
	    builder.registerTypeAdapter(Resource.class, new ResourceAdapter().nullSafe());
	    builder.enableComplexMapKeySerialization();
	    gson = builder.create();
	}
	return gson;
    }

    public static boolean hasFoodInfo(GItem item) {
	try {
	    return item.info().stream().anyMatch(i -> i instanceof FoodInfo);
	} catch (Loading ignored) {}
	return false;
    }

    public static void modifyFoodTooltip(ItemInfo.Owner owner, Collection<BufferedImage> imgs, int[] types, double glut, double fepSum) {
	imgs.add(RichText.render(String.format("Base FEP: $col[128,255,0]{%s}, FEP/Hunger: $col[128,255,0]{%s}", Utils.odformat2(fepSum, 2), FEPPerHunger(glut, fepSum)), 0).img);
	
	//this is not real item, don't add extra info
	if(!(owner instanceof GItem)) {return;}
	CharacterInfo character = null;
	CharacterInfo.Constipation constipation = null;
	try {
	    character = owner.context(Session.class).character;
	    constipation = character.constipation;
	} catch (NullPointerException | OwnerContext.NoContext ignore) {}

	if(character == null) {return;}
	
	List<FEPMod> mods = new ArrayList<>();
	boolean showCategories = CFG.DISPLAY_FOOD_CATEGORIES.get();

	character.getEnergyFEPMod().ifPresent(mods::add);
	
	//satiation
	if(types.length > 0) {
	    //TODO: find a way to get actual categories like meat, dairy, offal etc.
	    if(showCategories) {imgs.add(Text.render("Categories:").img);}

	    double satiation = 1;
	    for (int type : types) {
		CharacterInfo.Constipation.Data c = constipation.get(type);
		if(c != null) {
		    if(showCategories) {imgs.add(constipation.render(FoodInfo.class, c));}
		    satiation = Math.min(satiation, c.value);
		}
	    }
	    if(satiation != 1) {mods.add(new FEPMod(satiation, "satiation"));}
	}
	
	//hunger
	if(Math.abs(character.gluttony - 1) > 0.005d) {
	    mods.add(new FEPMod(character.gluttony, "hunger"));
	}
	
	//TODO: add table bonuses

	//account
	character.getAccountFEPBonus().ifPresent(mods::add);

	if(mods.isEmpty()) {return;}
	double fullMult = 1;

	imgs.add(RichText.render("Effectiveness:").img);
	for (FEPMod mod : mods) {
	    imgs.add(mod.img());
	    fullMult *= mod.val;
	}
	double adjustedFEP = fepSum * fullMult;
	imgs.add(RichText.render(String.format("Adjusted FEP: %s, FEP/Hunger: $col[200,150,255]{%s}", RichText.color(Utils.odformat2(adjustedFEP, 2), color(fullMult)), FEPPerHunger(glut, adjustedFEP)), 0).img);
    }
    
    private static String FEPPerHunger(double glut, double fepSum) {
	return glut != 0
	    ? Utils.odformat2(fepSum / (1000d * glut), 3)
	    : fepSum == 0 ? "0" : "∞";
    }
    
    public static class FEPMod {
	public final double val;
	public final String text;
	private BufferedImage img;

	public FEPMod(double val, String text) {
	    this.val = val;
	    this.text = text;
	}
	
	public BufferedImage img() {
	    if(img == null) {
		img = RichText.render(String.format("     ×%s %s", RichText.color(String.format("%.2f", val), color(val)), text), 0).img;
	    }
	    return img;
	}
    }

    public interface ITipData {
	ItemInfo create(Session sess);
    }
    
    private static class WearData implements ITipData {
	public final int max;
	
	private WearData(int wear) {
	    max = wear;
	}
	
	@Override
	public ItemInfo create(Session sess) {
	    return ItemInfo.make(sess, "ui/tt/wear", null, 0, max);
	}
	
	public static WearData make(Integer wear) {
	    if(wear != null) {
		return new WearData(wear);
	    } else {
		return null;
	    }
	}
    }
    
    private static class ArmorData implements ITipData {
	private final Integer hard;
	private final Integer soft;
    
	public ArmorData(Pair<Integer, Integer> armor, QualityList q) {
	    QualityList.Quality single = q.single(Quality);
	    if(single == null) {
		single = QualityList.DEFAULT;
	    }
	    hard = (int) Math.round(armor.a / single.multiplier);
	    soft = (int) Math.round(armor.b / single.multiplier);
	}
	
	@Override
	public ItemInfo create(Session sess) {
	    return ItemInfo.make(sess, "ui/tt/armor", null, hard, soft);
	}
    }
    
    private static class GastronomyData implements ITipData {
	private final double glut;
	private final double fev;
    
	public GastronomyData(ItemInfo data, QualityList q) {
	    QualityList.Quality single = q.single(Quality);
	    if(single == null) {
		single = QualityList.DEFAULT;
	    }
	    glut = Reflect.getFieldValueDouble(data, "glut") / single.multiplier;
	    fev = Reflect.getFieldValueDouble(data, "fev") / single.multiplier;
	}
    
	@Override
	public ItemInfo create(Session sess) {
	    return ItemInfo.make(sess, "ui/tt/gast", null, glut, fev);
	}
    }
    
    
    private static class AttrData implements ITipData {
	private final Map<Resource, Integer> attrs;
    
	public AttrData(Map<Resource, Integer> attrs) {
	    this.attrs = attrs;
	}
    
	@Override
	public ItemInfo create(Session sess) {
	    Object[] params = params(sess);
	    return ItemInfo.make(sess, "ui/tt/attrmod", params);
	}
    
	public Object[] params(Session sess) {
	    Object[] params = new Object[2 * attrs.size() + 1];
	    params[0] = sess.getresidf(Resource.remote().loadwait("ui/tt/attrmod"));
	    int i = 1;
	    for (Map.Entry<Resource, Integer> a : attrs.entrySet()) {
		params[i] = sess.getresidf(a.getKey());
		params[i + 1] = a.getValue();
		i += 2;
	    }
	    return params;
	}

	public static Map<Resource, Integer> parseInfo(List<ItemInfo> attrs, QualityList q) {
	    return parse(ItemInfo.findall(AttrMod.class, attrs), q);
	}

	public static Map<Resource, Integer> parse(List<AttrMod> attrs, QualityList q) {
	    Map<Resource, Integer> parsed = new HashMap<>(attrs.size());
	    ItemInfo.parseAttrMods(parsed, attrs);
	    QualityList.Quality single = q.single(Quality);
	    if(single == null) {
		single = QualityList.DEFAULT;
	    }
	    double multiplier = single.multiplier;
	    return parsed.entrySet()
		.stream()
		.collect(Collectors.toMap(
		    Map.Entry::getKey,
		    e -> {
			double v = e.getValue() / multiplier;
			if(v > 0) {
			    return (int) Math.round(v);
			} else {
			    return (int) v;
			}
		    }
		));
	}
    
	public static AttrData make(Map<Resource, Integer> attrs) {
	    if(attrs != null) {
		return new AttrData(attrs);
	    }
	    return null;
	}
    }
    
    private static class SlotsData implements ITipData {
    
	private final int left;
	private final double pmin;
	private final double pmax;
	private final Resource[] attrs;
    
	public SlotsData(int left, double pmin, double pmax, Resource[] attrs) {
	    this.left = left;
	    this.pmin = pmin;
	    this.pmax = pmax;
	    this.attrs = attrs;
	}

	public static SlotsData make(ISlots info) {
	    return new SlotsData(info.left, info.pmin, info.pmax, info.attrs);
	}
        
	@Override
	public ItemInfo create(Session sess) {
	    List<Object> params = new ArrayList<>();
	    params.add(null);
	    params.add(pmin);
	    params.add(pmax);
	    if(attrs != null) {
		params.addAll(Arrays.stream(attrs)
		    .map(sess::getresidf)
		    .collect(Collectors.toList())
		);
	    }
	    params.add(null);
	    params.add(left);
	    return ItemInfo.make(sess, "ui/tt/slots", params.toArray());
	}
    }
    
    private static class SlottedData implements ITipData {
	public final double pmin;
	public final double pmax;
	public final Resource[] attrs;
	private final Map<Resource, Integer> bonuses;
	
	private SlottedData(double pmin, double pmax, Resource[] attrs, Map<Resource, Integer> bonuses) {
	    this.pmin = pmin;
	    this.pmax = pmax;
	    this.attrs = attrs;
	    this.bonuses = bonuses;
	}
	
	@Override
	public ItemInfo create(Session sess) {
	    List<Object> params = new ArrayList<>();
	    params.add(null);
	    params.add(pmin);
	    params.add(pmax);
	    if(attrs != null) {
		params.addAll(Arrays.stream(attrs)
		    .map(sess::getresidf)
		    .collect(Collectors.toList())
		);
	    }
	    AttrData make = AttrData.make(bonuses);
	    if(make != null) {
		params.add(new Object[]{make.params(sess)});
	    } else {
		params.add(new Object[0]);
	    }
	    return ItemInfo.make(sess, "ui/tt/slot", params.toArray());
	}

	public static SlottedData make(Slotted info, QualityList q) {
	    return new SlottedData(info.pmin, info.pmax, info.attrs, AttrData.parseInfo(info.sub, q));
	}
    }
    
    private static class ResourceAdapter extends TypeAdapter<Resource> {
	
	@Override
	public void write(JsonWriter writer, Resource resource) throws IOException {
	    writer.value(resource.name);
	}
	
	@Override
	public Resource read(JsonReader reader) throws IOException {
	    return Resource.remote().loadwait(reader.nextString());
	}
    }

    public static float getMaxCapacity(WItem item) {
	Level level = item.fullness.get();
	if(level != null) {return (float) level.max;}
	
	//TODO: find a better way - maybe config file?
	String name = item.item.resname();
	if(name.contains("/bucket")) {return 1000f;}
	if(name.endsWith("/glassjug")) {return 500f;}
	if(name.endsWith("/waterskin")) {return 300f;}
	if(name.endsWith("/waterflask")) {return 200f;}
	if(name.contains("/kuksa")) {return 80f;}

	return 0f;
    }

    public static class Content {
	private static final Pattern PARSE = Pattern.compile("([\\d.]*) ([\\w]+) of (.*)");
	public final String name;
	public final String unit;
	public final float count;
	public final QualityList q;
	
	public Content(String name, String unit, float count) {
	    this(name, unit, count, QualityList.make(Collections.emptyList()));
	}

	public Content(String name, String unit, float count, QualityList q) {
	    this.name = name;
	    this.unit = unit;
	    this.count = count;
	    this.q = q;
	}

	public static Content parse(String name) {
	    return parse(name, QualityList.make(Collections.emptyList()));
	}

	public static Content parse(String name, QualityList q) {
	    Matcher m = PARSE.matcher(name);
	    if(m.find()) {
		float count = 0;
		try {
		    count = Float.parseFloat(m.group(1));
		} catch (Exception ignored) {}
		return new Content(m.group(3), m.group(2), count, q);
	    }
	    return new Content(name, "", 1, q);
	}

	public String name() {
	    if("seeds".equals(unit)) {
		return String.format("Seeds of %s", name);
	    }
	    return name;
	}
    
	public boolean is(String what) {
	    if(name == null || what == null) {
		return false;
	    }
	    return name.contains(what);
	}
	
	public boolean empty() {return count == 0 || name == null;}
    
	public static final Content EMPTY = new Content(null, null, 0);
    }

    public static class DebugInfo extends ItemInfo.Tip {
	public static RichText.Foundry fnd = new RichText.Foundry(
	    TextAttribute.FAMILY, "monospaced",
	    TextAttribute.SIZE, 10,
	    TextAttribute.FOREGROUND, Color.LIGHT_GRAY
	);
	public final BufferedImage img;

	public DebugInfo(GItem item) {
	    super(item);
	    this.img = fnd.render(item.resname()).img;
	}

	@Override
	public int order() {return 20000;}

	public BufferedImage tipimg() {
	    return (img);
	}
    }
}
