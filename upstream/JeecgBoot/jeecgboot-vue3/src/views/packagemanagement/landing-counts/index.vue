<template><PackagePage :definition="definitions['landing-counts']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagelandingcounts" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
