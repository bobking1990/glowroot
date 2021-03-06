/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.fat.init;

import java.io.File;

import javax.annotation.Nullable;

import org.glowroot.agent.config.ConfigService;
import org.glowroot.agent.config.PluginCache;

class ViewerAgentModule {

    private final PluginCache pluginCache;
    private final ConfigService configService;

    ViewerAgentModule(File baseDir, @Nullable File glowrootJarFile) throws Exception {
        pluginCache = PluginCache.create(glowrootJarFile, true);
        configService = ConfigService.create(baseDir, pluginCache.pluginDescriptors());
    }

    ConfigService getConfigService() {
        return configService;
    }
}
