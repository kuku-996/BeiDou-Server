/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.gms.provider;

import org.gms.provider.wz.WZFiles;
import org.gms.provider.wz.XMLWZFile;

import java.nio.file.Files;
import java.nio.file.Path;

public class DataProviderFactory {
    private static DataProvider getWZ(Path in) {
        return new XMLWZFile(in);
    }

    public static DataProvider getDataProvider(WZFiles in) {
        Path basePath = in.getBaseFile();
        Path languagePath = in.getLanguageFile();
        DataProvider baseProvider = getWZ(basePath);
        if (!Files.exists(languagePath) || languagePath.equals(basePath)) {
            return baseProvider;
        }
        // 中文 WZ 只维护被本地化过的文件，缺失的文件继续回退到原始 WZ。
        return new LocalizedDataProvider(getWZ(languagePath), baseProvider);
    }

    /**
     * 返回指定 WZ XML 实际命中的相对路径，规则与 {@link #getDataProvider(WZFiles)} 一致：
     * 语言目录中存在对应 XML 时优先使用，否则回退到原始 wz 目录。
     */
    public static String getResolvedXmlPath(WZFiles in, String dataPath) {
        Path languageXml = in.getLanguageFile().resolve(dataPath + ".xml");
        Path baseXml = in.getBaseFile().resolve(dataPath + ".xml");
        Path resolved = Files.exists(languageXml) ? languageXml : baseXml;
        return resolved.toString().replace('\\', '/');
    }
}
