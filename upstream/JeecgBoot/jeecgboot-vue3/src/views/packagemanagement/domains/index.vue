<template><PackagePage :definition="definitions['domains']" /></template>
<script setup lang="ts">
defineOptions({ name: "PackageDomains" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
