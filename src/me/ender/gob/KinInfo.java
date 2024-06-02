package me.ender.gob;

import haven.*;
import me.ender.Reflect;

import java.util.Map;

public class KinInfo {
    
    public static final String BUDDY_NAME = "haven.res.ui.obj.buddy.Buddy";
    public static final String VILLAGE_MATE_NAME = "haven.res.ui.obj.buddy_v.Vilmate";
    
    private static Class<? extends GAttrib> BUDDY, VILLAGER;
    
    public final int group;
    public final String name;
    public final Tex rname;
    public final boolean isVillager;
    
    private KinInfo(Gob gob, int id, boolean isVillager) {
	this.isVillager = isVillager;
	GameUI gui = gob.context(GameUI.class);
	BuddyWnd.Buddy b;
	
	if(gui != null && gui.buddies != null && (b = gui.buddies.find(id)) != null) {
	    this.group = b.group;
	    this.name = b.name;
	    this.rname = b.rname().tex();
	} else {
	    this.group = 0;
	    this.name = "";
	    this.rname = null;
	}
    }
    
    public static KinInfo from(Gob gob, Map<Class<? extends GAttrib>, GAttrib> attrs) {
	GAttrib buddy = null, villager = null;
	if(BUDDY == null || VILLAGER == null) {
	    for (Map.Entry<Class<? extends GAttrib>, GAttrib> entry : attrs.entrySet()) {
		if(BUDDY == null && BUDDY_NAME.equals(entry.getKey().getName())) {
		    BUDDY = entry.getKey();
		    buddy = entry.getValue();
		}
		if(VILLAGER == null && VILLAGE_MATE_NAME.equals(entry.getKey().getName())) {
		    VILLAGER = entry.getKey();
		    villager = entry.getValue();
		}
	    }
	}
	if(buddy == null && BUDDY != null) {
	    buddy = gob.getattr(BUDDY);
	}
	if(villager == null && VILLAGER != null) {
	    villager = gob.getattr(VILLAGER);
	}
	if(buddy != null || villager != null) {
	    return from(gob, buddy, villager);
	}
	return null;
    }
    
    private static KinInfo from(Gob gob, GAttrib buddy, GAttrib villager) {
	int id = buddy != null
	    ? Reflect.getFieldValueInt(buddy, "id")
	    : -1;
	return new KinInfo(gob, id, villager != null);
    }
    
    public static boolean isFoe(Gob gob) {
	KinInfo ki = gob.kin();
	if(ki != null) {
	    //mark as foe if in RED(2) group or WHITE(0) and not villager
	    return ki.group == 2 || ki.group == 0 && !ki.isVillager;
	}
	return true;
    }
    
    public static boolean isKinInfo(Class<? extends GAttrib> cls) {
	String name = cls.getName();
	if(BUDDY_NAME.equals(name)) {
	    return true;
	}
	if(VILLAGE_MATE_NAME.equals(name)) {
	    return true;
	}
	return false;
    }
}
