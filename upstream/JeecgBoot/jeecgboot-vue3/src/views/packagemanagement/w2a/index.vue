<template><PackagePage :definition="definitions['w2a']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagew2a" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
