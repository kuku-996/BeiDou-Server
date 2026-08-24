package org.gms.client.inventory;

/** v83 equipped and cash-equipment slot identifiers used by bot decoration. */
public enum BodyPart {
    EQUIPPED_BASE(0),
    HAIR(0),
    CAP(1),
    FACE_ACCESSORY(2),
    EYE_ACCESSORY(3),
    EAR_ACCESSORY(4),
    COAT(5),
    LONGCOAT(5),
    PANTS(6),
    SHOES(7),
    GLOVE(8),
    CAPE(9),
    SHIELD(10),
    WEAPON(11),
    RING_1(12),
    RING_2(13),
    PET_EQUIP_1(14),
    RING_3(15),
    RING_4(16),
    PENDANT(17),
    TAMING_MOB(18),
    SADDLE(19),
    MOB_EQUIP(20),
    MEDAL(49),
    BELT(50),
    SHOULDER(51),
    BADGE(56),
    PENDANT_EXT(61),
    EQUIPPED_END(60),
    CASH_BASE(100),
    CASH_WEAPON(111),
    CASH_END(160),
    DRAGON_BASE(1000),
    DRAGON_MASK(1000),
    DRAGON_PENDANT(1001),
    DRAGON_WING(1002),
    DRAGON_TAIL(1003),
    DRAGON_END(1100),
    MECHANIC_BASE(1100),
    MECHANIC_ENGINE(1100),
    MECHANIC_ARM(1101),
    MECHANIC_LEG(1102),
    MECHANIC_FRAME(1103),
    MECHANIC_TRANSISTOR(1104),
    MECHANIC_END(1200);

    private final int value;

    BodyPart(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
