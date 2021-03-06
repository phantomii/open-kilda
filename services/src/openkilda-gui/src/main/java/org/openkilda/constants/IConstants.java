package org.openkilda.constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Interface IConstants.
 *
 * @author Gaurav Chugh
 */
public abstract class IConstants {

    public static final String SESSION_OBJECT = "sessionObject";

    public class Role {
        public static final String ADMIN = "ROLE_ADMIN";
        public static final String USER = "ROLE_USER";
    }

    public class Permission {
    	public static final String MENU_TOPOLOGY = "menu_topology";
    	public static final String MENU_FLOWS = "menu_flows";
    	public static final String MENU_ISL = "menu_isl";
    	public static final String MENU_SWITCHES = "menu_switches";
    	public static final String MENU_USER_MANAGEMENT = "menu_user_management";
    	public static final String MENU_USER_ACTIVITY = "menu_user_activity";
    	
    	public static final String UM_ROLE = "um_role";
    	public static final String UM_PERMISSION = "um_permission";
    	
    	public static final String UM_USER_ADD = "um_user_add";
    	public static final String UM_USER_EDIT = "um_user_edit";
    	public static final String UM_USER_DELETE = "um_user_delete";
    	public static final String UM_USER_RESET = "um_user_reset";
    	public static final String UM_USER_RESET_ADMIN = "um_user_reset_admin";
    	public static final String UM_USER_RESET2FA = "um_user_reset2fa";
    	public static final String UM_ROLE_ADD = "um_role_add";
    	public static final String UM_ROLE_EDIT = "um_role_edit";
    	public static final String UM_ROLE_DELETE = "um_role_delete";
    	public static final String UM_ROLE_VIEW_USERS = "um_role_view_users";
    	public static final String UM_PERMISSION_ADD = "um_permission_add";
    	public static final String UM_PERMISSION_EDIT = "um_permission_edit";
    	public static final String UM_PERMISSION_DELETE = "um_permission_delete";
    	public static final String UM_PERMISSION_VIEW_ROLES = "um_permission_view_roles";
    	public static final String UM_PERMISSION_ASSIGN_ROLES = "um_permission_assign_roles";
    	public static final String UM_ASSIGN_ROLE_TO_USERS = "um_assign_role_to_users";
    	
    	public static final String UM_USER_ACTIVATE = "um_user_activate";
    	public static final String UM_PERMISSION_ACTIVATE = "um_permission_activate";
    	public static final String UM_ROLE_ASSIGN_USERS = "um_role_assign_users";
    	public static final String UM_ASSIGN_PERMISSION_TO_ROLES = "um_assign_permission_to_roles";
    	
    	public static final String SW_PERMISSION_RULES = "sw_permission_rules";
    	public static final String FW_PERMISSION_REROUTE = "fw_permission_reroute";
    	public static final String ISL_PERMISSION_EDITCOST = "isl_permission_editcost";
    	public static final String FW_PERMISSION_VALIDATE = "fw_permission_validate";
    }
    
    public class SETTINGS {
        public static final String TOPOLOGY_SETTING = "topology_setting";
    }


    public class View {
        public static final String ERROR = "errors/error";
        public static final String ERROR_403 = "errors/403";
        public static final String LOGIN = "login/login";
        public static final String HOME = "home";
        public static final String TOPOLOGY = "topology/topology";
        public static final String LOGOUT = "login/logout";
        public static final String REDIRECT_HOME = "redirect:/home";
        public static final String REDIRECT_LOGIN = "redirect:/login";
        public static final String SWITCH = "switch/switchdetails";
        public static final String ISL = "isl/isl";
        public static final String ISL_LIST = "isl/isllist";
        public static final String FLOW_LIST = "flows/flows";
        public static final String FLOW_DETAILS = "flows/flowdetails";
        public static final String PORT_DETAILS = "port/portdetails";
        public static final String SWITCH_LIST = "switch/switch";
        public static final String USERMANAGEMENT = "usermanagement/usermanagement";
        public static final String TWO_FA_GENERATOR = "login/twofa";
        public static final String OTP = "login/otp";
        public static final String ACTIVITY_LOGS= "useractivity/useractivity";
    }

    public enum Metrics {

        PEN_FLOW_BITS("Flow_bits", "pen.flow.bits"),

        PEN_FLOW_BYTES("Flow_bytes", "pen.flow.bytes"),

        PEN_FLOW_PACKETS("Flow_packets", "pen.flow.packets"),

        PEN_FLOW_TABLEID("Flow_tableid", "pen.flow.tableid"),

        PEN_ISL_LATENCY("Isl_latency", "pen.isl.latency"),

        PEN_SWITCH_COLLISIONS("Switch_collisions", "pen.switch.collisions"),

        PEN_SWITCH_RX_CRC_ERROR("Switch_crcerror", "pen.switch.rx-crc-error"),

        PEN_SWITCH_RX_FRAME_ERROR("Switch_frameerror", "pen.switch.rx-frame-error"),

        PEN_SWITCH_RX_OVER_ERROR("Switch_overerror", "pen.switch.rx-over-error"),

        PEN_SWITCH_RX_BITS("Switch_bits", "pen.switch.rx-bits"),

        PEN_SWITCH_TX_BITS("Switch_bits", "pen.switch.tx-bits"),

        PEN_SWITCH_RX_BYTES("Switch_bytes", "pen.switch.rx-bytes"),

        PEN_SWITCH_TX_BYTES("Switch_bytes", "pen.switch.tx-bytes"),

        PEN_SWITCH_RX_DROPPED("Switch_drops", "pen.switch.rx-dropped"),

        PEN_SWITCH_TX_DROPPED("Switch_drops", "pen.switch.tx-dropped"),

        PEN_SWITCH_RX_ERRORS("Switch_errors", "pen.switch.rx-errors"),

        PEN_SWITCH_TX_ERRORS("Switch_errors", "pen.switch.tx-errors"),

        PEN_SWITCH_TX_PACKETS("Switch_packets", "pen.switch.tx-packets"),

        PEN_SWITCH_RX_PACKETS("Switch_packets", "pen.switch.rx-packets");

        private String tag;
        private String displayTag;

        private Metrics(final String tag, final String displayTag) {
            setTag(tag);
            setDisplayTag(displayTag);
        }

        private void setTag(final String tag) {
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }

        private void setDisplayTag(final String displayTag) {
            this.displayTag = displayTag;
        }

        public String getDisplayTag() {
            return displayTag;
        }

        public static List<String> flowValue(String tag) {
            List<String> list = new ArrayList<String>();
            tag = "Flow_" + tag;
            for (Metrics metric : values()) {
                if (metric.getTag().equalsIgnoreCase(tag)) {
                    list.add(metric.getDisplayTag());
                    list.add(metric.getDisplayTag());
                }
            }
            return list;
        }

        public static List<String> switchValue(String tag) {
            List<String> list = new ArrayList<String>();

            if(tag.equalsIgnoreCase("latency")) {
                tag = "Isl_" + tag;
            } else {
                tag = "Switch_" + tag;
            }
            for (Metrics metric : values()) {
                if (metric.getTag().equalsIgnoreCase(tag)) {
                    list.add(metric.getDisplayTag());
                }
            }
            return list;
        }

        public static List<String> list() {
            List<String> list = new ArrayList<String>();
            for (Metrics metric : values()) {
                list.add(metric.getDisplayTag());
            }
            return list;
        }

        public static Set<String> tags() {
            Set<String> tags = new TreeSet<String>();
            for (Metrics metric : values()) {
                String[] v = metric.getTag().split("_");
                tags.add(v[1]);
            }
            return tags;
        }
    }

}
