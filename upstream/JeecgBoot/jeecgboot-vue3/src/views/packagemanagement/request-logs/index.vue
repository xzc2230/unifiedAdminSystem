<template><PackagePage :definition="definitions['request-logs']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagerequestlogs" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
