<template><PackagePage :definition="definitions['store']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagestore" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
