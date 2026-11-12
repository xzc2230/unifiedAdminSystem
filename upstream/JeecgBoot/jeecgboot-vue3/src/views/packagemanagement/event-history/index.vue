<template><PackagePage :definition="definitions['event-history']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packageeventhistory" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
