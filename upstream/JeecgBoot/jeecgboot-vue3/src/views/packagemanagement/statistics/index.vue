<template><PackagePage :definition="definitions['statistics']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagestatistics" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
