
package fr.pilato.elasticsearch.crawler.plugins.welcome;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerPlugin;

public class WelcomePlugin extends FsCrawlerPlugin {
    @Override
    protected String getName() {
        return "welcome";
    }

}
