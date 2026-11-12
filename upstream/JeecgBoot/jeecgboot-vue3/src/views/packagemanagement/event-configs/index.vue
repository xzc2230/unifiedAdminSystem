<template><PackagePage :definition="definitions['event-configs']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packageeventconfigs" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
