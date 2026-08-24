package org.gms.scripting.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NPCScriptDebugInfoTest {

    @Test
    void gm6DecoratesOnlyTheFirstNonEmptyNpcMessage() {
        NPCScriptDebugInfo debugInfo = NPCScriptDebugInfo.forNpc(9000000, "scripts-zh-CN/npc/9000000.js");

        String firstMessage = NPCScriptDebugInfo.decorate(debugInfo, 6, "原始 NPC 对话");
        String secondMessage = NPCScriptDebugInfo.decorate(debugInfo, 6, "第二页对话");

        assertTrue(firstMessage.contains("[GM6 脚本调试]"));
        assertTrue(firstMessage.contains("NPC ID：9000000"));
        assertTrue(firstMessage.contains("脚本：scripts-zh-CN/npc/9000000.js"));
        assertTrue(firstMessage.endsWith("原始 NPC 对话"));
        assertEquals("第二页对话", secondMessage);
    }

    @Test
    void nonGm6NeverSeesDebugInformation() {
        NPCScriptDebugInfo gm5Info = NPCScriptDebugInfo.forNpc(9000000, "scripts/npc/9000000.js");
        NPCScriptDebugInfo playerInfo = NPCScriptDebugInfo.forNpc(9000000, "scripts/npc/9000000.js");

        assertEquals("原始 NPC 对话", NPCScriptDebugInfo.decorate(gm5Info, 5, "原始 NPC 对话"));
        assertEquals("原始 NPC 对话", NPCScriptDebugInfo.decorate(playerInfo, 0, "原始 NPC 对话"));
    }

    @Test
    void questHeaderContainsNpcQuestStageAndResolvedScript() {
        NPCScriptDebugInfo debugInfo = NPCScriptDebugInfo.forQuest(2040000, 1001, false, "scripts/quest/1001.js");

        String message = NPCScriptDebugInfo.decorate(debugInfo, 6, "原始任务对话");

        assertTrue(message.contains("[GM6 任务调试]"));
        assertTrue(message.contains("NPC ID：2040000"));
        assertTrue(message.contains("任务 ID：1001"));
        assertTrue(message.contains("阶段：END"));
        assertTrue(message.contains("脚本：scripts/quest/1001.js"));
    }

    @Test
    void emptyOrMissingContextDoesNotAlterTextOrConsumeTheFirstMessage() {
        NPCScriptDebugInfo debugInfo = NPCScriptDebugInfo.forQuest(2040000, 1001, true, "scripts/quest/1001.js");

        assertEquals("原始对话", NPCScriptDebugInfo.decorate(null, 6, "原始对话"));
        assertEquals("", NPCScriptDebugInfo.decorate(debugInfo, 6, ""));
        assertEquals(null, NPCScriptDebugInfo.decorate(debugInfo, 6, null));

        String firstNonEmptyMessage = NPCScriptDebugInfo.decorate(debugInfo, 6, "真正第一页");
        assertTrue(firstNonEmptyMessage.contains("阶段：START"));
        assertTrue(firstNonEmptyMessage.endsWith("真正第一页"));
    }

    @Test
    void separateConversationContextsDoNotLeakConsumptionState() {
        NPCScriptDebugInfo firstConversation = NPCScriptDebugInfo.forNpc(1000, "scripts/npc/1000.js");
        NPCScriptDebugInfo secondConversation = NPCScriptDebugInfo.forNpc(2000, "scripts/npc/2000.js");

        NPCScriptDebugInfo.decorate(firstConversation, 6, "第一段会话");
        String secondConversationMessage = NPCScriptDebugInfo.decorate(secondConversation, 6, "第二段会话");

        assertFalse(NPCScriptDebugInfo.decorate(firstConversation, 6, "第一段第二页").contains("[GM6 脚本调试]"));
        assertTrue(secondConversationMessage.contains("NPC ID：2000"));
        assertTrue(secondConversationMessage.endsWith("第二段会话"));
    }

    @Test
    void gm6XmlQuestMessageContainsResolvedFilesAndPhaseNodes() {
        String message = NPCScriptDebugInfo.forXmlQuest(6, 1012000, 1000, true,
                "wz-zh-CN/Quest.wz/QuestInfo.img.xml",
                "wz-zh-CN/Quest.wz/Check.img.xml",
                "wz-zh-CN/Quest.wz/Act.img.xml");

        assertTrue(message.contains("[GM6 XML任务调试]"));
        assertTrue(message.contains("任务 ID：1000"));
        assertTrue(message.contains("阶段：START"));
        assertTrue(message.contains("QuestInfo.img.xml/1000"));
        assertTrue(message.contains("Check.img.xml/1000/0"));
        assertTrue(message.contains("Act.img.xml/1000/0"));
    }

    @Test
    void nonGm6DoesNotReceiveXmlQuestMessage() {
        assertEquals(null, NPCScriptDebugInfo.forXmlQuest(5, 1012000, 1000, false,
                "QuestInfo.img.xml", "Check.img.xml", "Act.img.xml"));
    }
}
