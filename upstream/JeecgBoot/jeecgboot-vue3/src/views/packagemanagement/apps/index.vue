<template><PackagePage :definition="definitions['apps']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packageapps" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
