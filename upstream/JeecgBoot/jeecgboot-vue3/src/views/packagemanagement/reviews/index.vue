<template><PackagePage :definition="definitions['reviews']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagereviews" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
