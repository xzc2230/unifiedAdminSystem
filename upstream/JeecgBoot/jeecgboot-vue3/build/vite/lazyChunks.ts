// Keep TinyMCE core/plugins/shim together; shared runtime stays in automatic chunks.
export const lazyChunks = {
  groups: [{
    name: 'tinymce-vendor',
    test: (id: string) => /(?:node_modules[/\\](?:tinymce|@tinymce[/\\]tinymce-vue)[/\\]|[/\\]src[/\\]utils[/\\]tinymceGlobalShim)/.test(id),
    includeDependenciesRecursively: false,
  }],
};
