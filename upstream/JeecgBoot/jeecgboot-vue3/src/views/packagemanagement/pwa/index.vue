<template><PackagePage :definition="definitions['pwa']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagepwa" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
