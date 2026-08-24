package org.gms.scripting.npc;

/**
 * 会话内的 GM6 脚本调试文本。每个会话实例只会装饰第一条非空文本。
 */
public final class NPCScriptDebugInfo {
    private static final int DEBUG_GM_LEVEL = 6;

    private final String header;
    private boolean consumed;

    private NPCScriptDebugInfo(String header) {
        this.header = header;
    }

    public static NPCScriptDebugInfo forNpc(int npcId, String scriptPath) {
        return new NPCScriptDebugInfo("#r[GM6 脚本调试]#k\r\n"
                + "NPC ID：" + npcId + "\r\n"
                + "脚本：" + scriptPath + "\r\n"
                + "----------------");
    }

    public static NPCScriptDebugInfo forQuest(int npcId, int questId, boolean start, String scriptPath) {
        return new NPCScriptDebugInfo("#r[GM6 任务调试]#k\r\n"
                + "NPC ID：" + npcId + "\r\n"
                + "任务 ID：" + questId + "\r\n"
                + "阶段：" + (start ? "START" : "END") + "\r\n"
                + "脚本：" + scriptPath + "\r\n"
                + "----------------");
    }

    public static String forXmlQuest(int gmLevel, int npcId, int questId, boolean start,
                                     String questInfoPath, String checkPath, String actPath) {
        if (gmLevel < DEBUG_GM_LEVEL) {
            return null;
        }

        String phase = start ? "START" : "END";
        int phaseNode = start ? 0 : 1;
        return "#r[GM6 XML任务调试]#k\r\n"
                + "NPC ID：" + npcId + "\r\n"
                + "任务 ID：" + questId + "\r\n"
                + "阶段：" + phase + "\r\n"
                + "任务信息：" + questInfoPath + "/" + questId + "\r\n"
                + "条件节点：" + checkPath + "/" + questId + "/" + phaseNode + "\r\n"
                + "动作节点：" + actPath + "/" + questId + "/" + phaseNode;
    }

    public static String decorate(NPCScriptDebugInfo debugInfo, int gmLevel, String text) {
        return debugInfo == null ? text : debugInfo.decorate(gmLevel, text);
    }

    private String decorate(int gmLevel, String text) {
        if (gmLevel < DEBUG_GM_LEVEL || consumed || text == null || text.isEmpty()) {
            return text;
        }

        consumed = true;
        return header + "\r\n" + text;
    }
}
