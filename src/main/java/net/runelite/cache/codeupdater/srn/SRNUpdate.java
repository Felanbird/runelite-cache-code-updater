/*
 * Copyright (c) 2019 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.codeupdater.srn;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.IndexType;
import net.runelite.cache.ItemManager;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.ItemSpriteFactory;

@Slf4j
public class SRNUpdate
{
	private static ModelProvider modelProvider(Store store)
	{
		Index models = store.getIndex(IndexType.MODELS);
		ModelLoader ml = new ModelLoader();
		return modelId -> {
			Archive archive = models.getArchive(modelId);
			if (archive == null)
			{
				return null;
			}
			byte[] data = archive.decompress(store.getStorage().loadArchive(archive));
			return ml.load(modelId, data);
		};
	}

	private static Map<Integer, ItemDefinition> filterAndMapForCount(ItemManager im)
	{
		Collection<ItemDefinition> items = im.getItems();
		Map<Integer, ItemDefinition> itemIDs = new HashMap<>(items.size());
		for (ItemDefinition d : items)
		{
			if (d.notedTemplate != -1 || d.placeholderTemplateId != -1)
			{
				continue;
			}
			itemIDs.put(d.id, d);
		}
		for (ItemDefinition d : items)
		{
			// Remove all count variants, except the highest one, which we map to the root
			if (d.countObj != null)
			{
				int hiCo = 1;
				int hiObj = d.id;
				for (int i = 0; i < 10; i++)
				{
					if (d.countCo[i] == 0)
					{
						continue;
					}
					itemIDs.remove(d.countObj[i]);
					if (hiCo <= d.countCo[i])
					{
						hiObj = d.countObj[i];
						hiCo = d.countCo[i];
					}
				}
				itemIDs.put(d.id, im.getItem(hiObj));
			}
		}
		return itemIDs;
	}

	public static void update() throws IOException
	{
		ItemManager nim = new ItemManager(Main.next);
		nim.load();
		Map<Integer, ItemDefinition> nis = filterAndMapForCount(nim);
		ModelProvider nmm = modelProvider(Main.next);
		SpriteManager nsm = new SpriteManager(Main.next);
		nsm.load();
		TextureManager ntm = new TextureManager(Main.next);
		ntm.load();

		ItemManager pim = new ItemManager(Main.previous);
		pim.load();
		Map<Integer, ItemDefinition> pis = filterAndMapForCount(pim);
		ModelProvider pmm = modelProvider(Main.previous);
		SpriteManager psm = new SpriteManager(Main.previous);
		psm.load();
		TextureManager ptm = new TextureManager(Main.previous);
		ptm.load();

		MutableCommit imCommit = new MutableCommit("Item Icons");

		IntPredicate isSpriteChanged = sid -> {
			SpriteDefinition nsd = nsm.findSprite(sid, 0);
			SpriteDefinition psd = psm.findSprite(sid, 0);
			return !Objects.equals(nsd, psd);
		};

		IntPredicate isTextureChanged = tid -> {
			TextureDefinition ntd = ntm.findTexture(tid);
			TextureDefinition ptd = ptm.findTexture(tid);
			if (!Objects.equals(ntd, ptd))
			{
				return true;
			}
			if (ntd == null)
			{
				return false;
			}
			return IntStream.of(ntd.getFileIds()).anyMatch(isSpriteChanged);

		};

		Predicate<short[]> anyTextureChanged = tids -> {
			if (tids == null)
			{
				return false;
			}

			for (short tid : tids)
			{
				if (isTextureChanged.test(tid))
				{
					return true;
				}
			}
			return false;
		};

		IntPredicate isModelChanged = mid -> {
			try
			{
				ModelDefinition nmd = nmm.provide(mid);
				ModelDefinition pmd = pmm.provide(mid);
				if (!Objects.equals(nmd, pmd))
				{
					return true;
				}
				return anyTextureChanged.test(nmd.faceTextures);
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		};

		Main.execAllAndWait(nis.entrySet().stream()
			.filter(item -> {
				ItemDefinition nid = item.getValue();
				ItemDefinition pid = pis.get(item.getKey());
				if (!nid.equals(pid))
				{
					return true;
				}
				if (anyTextureChanged.test(nid.textureReplace))
				{
					return true;
				}
				return isModelChanged.test(nid.inventoryModel);
			})
			.map(item -> () -> {
				try
				{
					imCommit.log("{},", item.getKey());
					BufferedImage img = ItemSpriteFactory.createSprite(
						nim, nmm, nsm, ntm,
						item.getValue().id, 0, 1, 0x302020, false);
					try (OutputStream os = imCommit.writeFile("cache/item/icon/" + item.getKey() + ".png"))
					{
						ImageIO.write(img, "png", os);
					}
				}
				catch (Exception e)
				{
					log.warn("Unable to create sprite for {} ({})", item.getKey(), item.getValue().id, e);
				}
			}));

		imCommit.finish(Repo.SRN.get(), Main.branchName);

		Map<Integer, String> itemNames = nim.getItems().stream()
			.filter(i -> !Strings.isNullOrEmpty(i.name) && !"null".equalsIgnoreCase(i.name))
			.collect(ImmutableMap.toImmutableMap(
				i -> i.id,
				i -> i.name
			));

		String itemNameJSON = new GsonBuilder()
			.disableHtmlEscaping()
			.setPrettyPrinting()
			.create()
			.toJson(itemNames);

		MutableCommit nameCommit = new MutableCommit("Item Names");
		nameCommit.writeFile("cache/item/names.json", itemNameJSON.getBytes());
		nameCommit.finish(Repo.SRN.get(), Main.branchName);
	}
}