/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.sulphurnagua;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import omni.osrs.plugin.OmniPlugin;
import omni.osrs.plugin.OmniPluginProvider;
import omni.osrs.plugin.OmniPluginRuntime;
import omni.osrs.plugin.OmniPluginSetting;
import omni.osrs.runtime.OmniGame;

/**
 * Entry point named by {@code META-INF/omni-plugin.properties} for {@link SulphurNaguaPlugin}.
 */
public final class SulphurNaguaProvider implements OmniPluginProvider
{
	private static final String ID = "omnisulphurnagua";
	private static final String VERSION = descriptorVersion();

	@Override
	public String id()
	{
		return ID;
	}

	@Override
	public String name()
	{
		return "Omni Sulphur Nagua";
	}

	@Override
	public String description()
	{
		return "Fights Sulphur Naguas in Cam Torum: drinks Moonlight potions, brews new ones from grubs and vials, "
			+ "loots runes and ore, and can trade essence for Runecrafting experience.";
	}

	@Override
	public String category()
	{
		return "Combat";
	}

	@Override
	public String version()
	{
		// Must equal version= in the descriptor, which a release build may rewrite: read it back.
		return VERSION;
	}

	@Override
	public List<OmniPluginSetting> settings()
	{
		return SulphurNaguaConfig.settings();
	}

	@Override
	public String incompatibility(OmniGame game)
	{
		return game.unsupportedFeatures();
	}

	@Override
	public OmniPlugin create(OmniPluginRuntime runtime)
	{
		return new SulphurNaguaPlugin(runtime, SulphurNaguaConfig.from(runtime.settings()));
	}

	private static String descriptorVersion()
	{
		try
		{
			Enumeration<URL> descriptors = SulphurNaguaProvider.class.getClassLoader()
				.getResources("META-INF/omni-plugin.properties");
			while (descriptors.hasMoreElements())
			{
				Properties properties = new Properties();
				try (InputStream in = descriptors.nextElement().openStream())
				{
					properties.load(in);
				}
				if (ID.equals(properties.getProperty("id")) && properties.getProperty("version") != null)
				{
					return properties.getProperty("version").trim();
				}
			}
		}
		catch (IOException ignored)
		{
			// Falls through to the source's own version.
		}
		return "1.0.0";
	}
}
