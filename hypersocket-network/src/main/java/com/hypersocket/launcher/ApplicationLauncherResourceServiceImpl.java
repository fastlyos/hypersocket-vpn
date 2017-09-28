package com.hypersocket.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import com.hypersocket.attributes.user.UserAttributeService;
import com.hypersocket.events.EventService;
import com.hypersocket.http.HttpUtilsImpl;
import com.hypersocket.i18n.I18NService;
import com.hypersocket.launcher.events.ApplicationLauncherCreatedEvent;
import com.hypersocket.launcher.events.ApplicationLauncherDeletedEvent;
import com.hypersocket.launcher.events.ApplicationLauncherEvent;
import com.hypersocket.launcher.events.ApplicationLauncherUpdatedEvent;
import com.hypersocket.menus.AbstractTableAction;
import com.hypersocket.menus.MenuRegistration;
import com.hypersocket.menus.MenuService;
import com.hypersocket.netty.HttpRequestDispatcherHandler;
import com.hypersocket.network.NetworkResourceServiceImpl;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.permissions.PermissionCategory;
import com.hypersocket.permissions.PermissionService;
import com.hypersocket.properties.EntityResourcePropertyStore;
import com.hypersocket.realm.Realm;
import com.hypersocket.realm.RealmService;
import com.hypersocket.resource.AbstractResourceRepository;
import com.hypersocket.resource.AbstractResourceServiceImpl;
import com.hypersocket.resource.ResourceCreationException;
import com.hypersocket.resource.ResourceException;
import com.hypersocket.tables.BootstrapTableResult;
import com.hypersocket.transactions.TransactionService;
import com.hypersocket.ui.IndexPageFilter;

@Service
public class ApplicationLauncherResourceServiceImpl extends AbstractResourceServiceImpl<ApplicationLauncherResource>
		implements ApplicationLauncherResourceService {

	static Logger log = LoggerFactory.getLogger(ApplicationLauncherResourceServiceImpl.class);

	public static final String RESOURCE_BUNDLE = "LauncherService";

	public static final String APPLICATION_LAUNCHER_ACTIONS = "applicationLauncherActions";

	@Autowired
	ApplicationLauncherResourceRepository repository;

	@Autowired
	I18NService i18nService;

	@Autowired
	PermissionService permissionService;

	@Autowired
	MenuService menuService;

	@Autowired
	EventService eventService;

	@Autowired
	RealmService realmService;

	@Autowired
	UserAttributeService attributeService;

	@Autowired
	TransactionService transactionService;
	@Autowired
	IndexPageFilter indexPage;

	@Autowired
	HttpUtilsImpl httpUtils;

	private Map<String, ApplicationLauncherTemplateResolver> templateResolvers = new HashMap<>();

	public ApplicationLauncherResourceServiceImpl() {
		super("applicationLauncher");
	}

	@PostConstruct
	private void postConstruct() {

		i18nService.registerBundle(RESOURCE_BUNDLE);

		PermissionCategory cat = permissionService.registerPermissionCategory(RESOURCE_BUNDLE, "category.lauchers");

		repository.loadPropertyTemplates("applicationLauncherTemplate.xml");

		for (ApplicationLauncherResourcePermission p : ApplicationLauncherResourcePermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		menuService.registerMenu(
				new MenuRegistration(RESOURCE_BUNDLE, "launchers", "fa-desktop", "launchers", 9999,
						ApplicationLauncherResourcePermission.READ, ApplicationLauncherResourcePermission.CREATE,
						ApplicationLauncherResourcePermission.UPDATE, ApplicationLauncherResourcePermission.DELETE),
				NetworkResourceServiceImpl.MENU_NETWORK);

		menuService.registerTableAction(APPLICATION_LAUNCHER_ACTIONS, new AbstractTableAction("launch", "fa-rocket",
						null, ApplicationLauncherResourcePermission.READ, 900, null, "canLaunch"));

		eventService.registerEvent(ApplicationLauncherEvent.class, RESOURCE_BUNDLE, this);
		eventService.registerEvent(ApplicationLauncherCreatedEvent.class, RESOURCE_BUNDLE, this);
		eventService.registerEvent(ApplicationLauncherUpdatedEvent.class, RESOURCE_BUNDLE, this);
		eventService.registerEvent(ApplicationLauncherDeletedEvent.class, RESOURCE_BUNDLE, this);

		indexPage.addScript("${uiPath}/js/launchers.js");
		
		EntityResourcePropertyStore.registerResourceService(ApplicationLauncherResource.class, repository);
	}

	@Override
	protected AbstractResourceRepository<ApplicationLauncherResource> getRepository() {
		return repository;
	}

	@Override
	protected String getResourceBundle() {
		return RESOURCE_BUNDLE;
	}

	@Override
	public Class<ApplicationLauncherResourcePermission> getPermissionType() {
		return ApplicationLauncherResourcePermission.class;
	}

	protected Class<ApplicationLauncherResource> getResourceClass() {
		return ApplicationLauncherResource.class;
	}

	@Override
	protected void fireResourceCreationEvent(ApplicationLauncherResource resource) {
		eventService.publishEvent(new ApplicationLauncherCreatedEvent(this, getCurrentSession(), resource));
	}

	@Override
	protected void fireResourceCreationEvent(ApplicationLauncherResource resource, Throwable t) {
		eventService.publishEvent(new ApplicationLauncherCreatedEvent(this, resource, t, getCurrentSession()));
	}

	@Override
	protected void fireResourceUpdateEvent(ApplicationLauncherResource resource) {
		eventService.publishEvent(new ApplicationLauncherUpdatedEvent(this, getCurrentSession(), resource));
	}

	@Override
	protected void fireResourceUpdateEvent(ApplicationLauncherResource resource, Throwable t) {
		eventService.publishEvent(new ApplicationLauncherUpdatedEvent(this, resource, t, getCurrentSession()));
	}

	@Override
	protected void fireResourceDeletionEvent(ApplicationLauncherResource resource) {
		eventService.publishEvent(new ApplicationLauncherDeletedEvent(this, getCurrentSession(), resource));
	}

	@Override
	protected void fireResourceDeletionEvent(ApplicationLauncherResource resource, Throwable t) {
		eventService.publishEvent(new ApplicationLauncherDeletedEvent(this, resource, t, getCurrentSession()));
	}

	@Override
	public ApplicationLauncherResource updateResource(ApplicationLauncherResource resource, String name,
			Map<String, String> properties) throws ResourceException, AccessDeniedException {

		resource.setName(name);

		updateResource(resource, properties);

		return resource;
	}

	@Override
	public ApplicationLauncherResource createResource(String name, Realm realm, Map<String, String> properties)
			throws ResourceException, AccessDeniedException {

		ApplicationLauncherResource resource = new ApplicationLauncherResource();
		resource.setName(name);
		resource.setRealm(realm);

		createResource(resource, properties);

		return resource;
	}

	@Override
	public BootstrapTableResult<?> searchTemplates(String resolver, String search, int iDisplayStart,
			int iDisplayLength) throws IOException, AccessDeniedException {

		assertPermission(ApplicationLauncherResourcePermission.CREATE);

		if (resolver == null) {
			/*
			 * If no resolver is specified we created a merged list manually.
			 * This could get horribly inefficient as the catalogue gets bigger
			 * so a better way will have to be found
			 * 
			 */
			List<Object> rows = new ArrayList<>();
			Object resource = null;
			for (Map.Entry<String, ApplicationLauncherTemplateResolver> en : templateResolvers.entrySet()) {
				BootstrapTableResult<?> b = en.getValue().resolveTemplates(search, 0, Integer.MAX_VALUE);
				if (resource == null)
					resource = b.getResource();
				rows.addAll(b.getRows());
			}
			long total = rows.size();
			if (!rows.isEmpty())
				rows = rows.subList(Math.max(0, iDisplayStart), Math.min(iDisplayStart + iDisplayLength, rows.size()));
			BootstrapTableResult<Object> r = new BootstrapTableResult<>();
			r.setRows(rows);
			r.setResource(resource);
			r.setTotal(total);
			return r;
		} else {
			ApplicationLauncherTemplateResolver resolverObj = templateResolvers.get(resolver);
			if (resolverObj == null)
				throw new IOException(String.format("Unknown resolver %s.", resolver));
			return resolverObj.resolveTemplates(search, iDisplayStart, iDisplayLength);
		}
	}

	@Override
	public void downloadTemplateImage(String uuid, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		request.setAttribute(HttpRequestDispatcherHandler.CONTENT_INPUTSTREAM,
				httpUtils
						.doHttpGet(
								System.getProperty("hypersocket.templateServerImageUrl",
										"https://updates2.hypersocket.com/hypersocket/api/templates/image/") + uuid,
								true));

	}

	@Override
	public ApplicationLauncherResource createFromTemplate(final String script)
			throws ResourceException, AccessDeniedException {

		assertPermission(ApplicationLauncherResourcePermission.CREATE);

		ApplicationLauncherResource result = transactionService
				.doInTransaction(new TransactionCallback<ApplicationLauncherResource>() {

					@Override
					public ApplicationLauncherResource doInTransaction(TransactionStatus status) {

						ScriptEngineManager manager = new ScriptEngineManager();
						ScriptEngine engine = manager.getEngineByName("beanshell");

						Bindings bindings = engine.createBindings();
						bindings.put("realmService", realmService);
						bindings.put("templateService", ApplicationLauncherResourceServiceImpl.this);
						bindings.put("attributeService", attributeService);
						bindings.put("log", log);

						try {
							Object result = engine.eval(script, bindings);
							if (result instanceof ApplicationLauncherResource) {
								return (ApplicationLauncherResource) result;
							} else {
								throw new IllegalStateException("Transaction failed", new ResourceCreationException(
										RESOURCE_BUNDLE, "error.templateFailed", "Script returned invalid object"));
							}
						} catch (ScriptException e) {
							log.error("Failed to create application launcher from template", e);
							if (e.getCause() instanceof ResourceCreationException) {
								throw new IllegalStateException("Transaction failed", e.getCause());
							}
							throw new IllegalStateException("Transaction failed", new ResourceCreationException(
									RESOURCE_BUNDLE, "error.templateFailed", e.getMessage()));
						}
					}

				});

		return result;

	}

	@Override
	public void registerTemplateResolver(ApplicationLauncherTemplateResolver resolver) {
		templateResolvers.put(resolver.getId(), resolver);
	}
}
