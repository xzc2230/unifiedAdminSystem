<template><PackagePage :definition="definitions['app-events']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packageappevents" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
